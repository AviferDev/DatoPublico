package es.aviferdev.datopublico.backend

import es.aviferdev.datopublico.backend.feed.DailyFeedService
import es.aviferdev.datopublico.backend.infra.AppConfig
import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.backend.ingesta.job.IngestionConfig
import es.aviferdev.datopublico.backend.ingesta.job.DailyIngestionJob
import es.aviferdev.datopublico.backend.ingesta.job.IngestionRunState
import es.aviferdev.datopublico.backend.ingesta.job.IngestionSchedule
import es.aviferdev.datopublico.backend.ingesta.job.IngestionScheduler
import es.aviferdev.datopublico.backend.ingesta.job.IngestionWatchdog
import es.aviferdev.datopublico.backend.ingesta.publicacion.BoePublicacionHttpParser
import es.aviferdev.datopublico.backend.ingesta.publicacion.BoeTextoHttpClient
import es.aviferdev.datopublico.backend.ingesta.publicacion.boeTextoHttpClient
import es.aviferdev.datopublico.backend.ingesta.sumario.BoeSumarioHttpClient
import es.aviferdev.datopublico.backend.ingesta.sumario.boeSumarioHttpClient
import es.aviferdev.datopublico.backend.persistence.FeedRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.PublicationRepositoryJdbc
import es.aviferdev.datopublico.backend.plugins.configureMonitoring
import es.aviferdev.datopublico.backend.plugins.configureRouting
import es.aviferdev.datopublico.backend.plugins.configureSerialization
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import javax.sql.DataSource
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Composition root del backend: lee la configuración del entorno y arranca Netty.
 *
 * El arranque no depende de la base de datos ni de servicios externos: si la
 * configuración de BD no resuelve, no se crea el pool, `/feed` no se registra y el
 * servidor sigue sirviendo `/health`.
 */
fun main() {
    val config = AppConfig.fromEnv()
    embeddedServer(
        Netty,
        port = config.port,
        host = config.host,
        module = Application::module,
    ).start(wait = true)
}

/**
 * Módulo Ktor: serialización, logging de llamadas, pool JDBC **único** compartido
 * por el feed y la ingesta, rutas e ingesta condicional.
 *
 * El pool lo crea el composition root (una sola vez) y lo cierra **una sola vez**
 * en `ApplicationStopped`; ni la ruta ni la ingesta lo cierran.
 */
fun Application.module() {
    configureSerialization()
    configureMonitoring()
    val dataSource = createDataSourceOrNull()
    configureRouting(feedServiceOf(dataSource))
    configureIngestion(dataSource)
    monitorDataSource(dataSource)
}

/**
 * Crea el pool JDBC **único** del backend, o `null` si la configuración de base de
 * datos no resuelve (`POSTGRES_PASSWORD` ausente, etc.). Un fallo **no** impide el
 * arranque: sin pool, el feed no se registra y la ingesta se omite.
 */
private fun Application.createDataSourceOrNull(): DataSource? =
    try {
        Database.createDataSource(DatabaseConfig.fromEnv())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        log.warn("Base de datos no configurada: {}", error.message)
        null
    }

/** Programa el cierre **único** del pool compartido al parar el servidor. */
private fun Application.monitorDataSource(dataSource: DataSource?) {
    if (dataSource != null) {
        monitor.subscribe(ApplicationStopped) { closeDataSource(dataSource) }
    }
}

/** Construye el feed sobre el pool compartido, o `null` si no hay base de datos. */
private fun feedServiceOf(dataSource: DataSource?): DailyFeedService? =
    if (dataSource == null) null else DailyFeedService(FeedRepositoryJdbc(dataSource))

/** Cierra el pool si su implementación lo permite (HikariCP lo es). */
private fun closeDataSource(dataSource: DataSource) {
    (dataSource as? AutoCloseable)?.close()
}

/**
 * Cablea la ingesta de forma **condicional y no bloqueante**.
 *
 * Solo se arranca si `INGESTA_ENABLED` está activo **y** hay pool (la config de
 * BD resuelve). Cualquier fallo al construirla se registra y el servidor sigue
 * arrancando; al parar se cancela el bucle y se cierran los clientes HTTP (el
 * pool lo cierra el composition root).
 */
private fun Application.configureIngestion(dataSource: DataSource?) {
    val config = IngestionConfig.fromEnv()
    if (config.enabled && dataSource != null) {
        startIngestion(config, dataSource)
    } else {
        log.info("Ingesta no programada (INGESTA_ENABLED=false o sin base de datos)")
    }
}

/** Arranca la ingesta y programa su cierre; un fallo no impide el arranque. */
private fun Application.startIngestion(config: IngestionConfig, dataSource: DataSource) {
    try {
        val runtime = createIngestionRuntime(config, dataSource)
        monitor.subscribe(ApplicationStopped) { runtime.close() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        log.warn("Ingesta no arrancada: {}", error.message)
    }
}

/**
 * Construye los recursos de la ingesta (clientes HTTP, job, scheduler y
 * vigilante) sobre el [dataSource] compartido y lanza sus bucles. Si algo falla,
 * cierra los clientes ya creados y propaga (el pool lo cierra el composition
 * root).
 */
private fun Application.createIngestionRuntime(
    config: IngestionConfig,
    dataSource: DataSource,
): IngestionRuntime {
    val summaryHttp = boeSumarioHttpClient()
    val textHttp = boeTextoHttpClient()
    return try {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val schedule = IngestionSchedule(config.times, config.zone)
        val runState = IngestionRunState()
        val scheduler = IngestionScheduler(
            schedule = schedule,
            job = DailyIngestionJob(
                summaryClient = BoeSumarioHttpClient(summaryHttp),
                publicationParser = BoePublicacionHttpParser(BoeTextoHttpClient(textHttp)),
                repository = PublicationRepositoryJdbc(dataSource),
            ),
            zone = config.zone,
            runState = runState,
        )
        val watchdog = IngestionWatchdog(
            schedule = schedule,
            runState = runState,
            grace = config.grace,
        )
        scheduler.start(scope)
        watchdog.start(scope)
        log.info(
            "Ingesta programada: horas={} zona={} gracia={}min",
            config.times,
            config.zone,
            config.grace.toMinutes(),
        )
        IngestionRuntime(scope, listOf(summaryHttp, textHttp))
    } catch (error: Exception) {
        summaryHttp.close()
        textHttp.close()
        throw error
    }
}

/**
 * Recursos vivos de la ingesta: el bucle del scheduler y los clientes HTTP.
 * [close] los libera en `ApplicationStopped`.
 */
private class IngestionRuntime(
    private val scope: CoroutineScope,
    private val httpClients: List<HttpClient>,
) : AutoCloseable {
    override fun close() {
        scope.cancel()
        httpClients.forEach(HttpClient::close)
    }
}
