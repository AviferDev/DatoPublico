package es.aviferdev.datopublico.backend

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
 * El arranque no depende de la base de datos ni de servicios externos; los
 * subsistemas sin configuración suficiente se omiten y el servidor sigue
 * sirviendo `/health`.
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

/** Módulo Ktor: serialización, logging de llamadas, rutas e ingesta condicional. */
fun Application.module() {
    configureSerialization()
    configureMonitoring()
    configureRouting()
    configureIngestion()
}

/**
 * Cablea la ingesta de forma **condicional y no bloqueante**.
 *
 * Solo se arranca si `INGESTA_ENABLED` está activo **y** la configuración de base
 * de datos resuelve (`POSTGRES_PASSWORD` presenta). Cualquier fallo al construirla
 * se registra y el servidor sigue arrancando; al parar la aplicación se cancela el
 * bucle y se cierran el pool y los clientes HTTP.
 */
private fun Application.configureIngestion() {
    val config = IngestionConfig.fromEnv()
    if (config.enabled) {
        startIngestion(config)
    } else {
        log.info("Ingesta deshabilitada (INGESTA_ENABLED=false)")
    }
}

/** Arranca la ingesta y programa su cierre; un fallo no impide el arranque. */
private fun Application.startIngestion(config: IngestionConfig) {
    try {
        val runtime = createIngestionRuntime(config)
        monitor.subscribe(ApplicationStopped) { runtime.close() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        log.warn("Ingesta no arrancada: {}", error.message)
    }
}

/**
 * Construye los recursos de la ingesta (pool JDBC, clientes HTTP, job, scheduler
 * y vigilante) y lanza sus bucles. Si algo falla, cierra lo ya creado y propaga.
 */
private fun Application.createIngestionRuntime(config: IngestionConfig): IngestionRuntime {
    val dataSource = Database.createDataSource(DatabaseConfig.fromEnv())
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
        IngestionRuntime(scope, dataSource, listOf(summaryHttp, textHttp))
    } catch (error: Exception) {
        summaryHttp.close()
        textHttp.close()
        (dataSource as? AutoCloseable)?.close()
        throw error
    }
}

/**
 * Recursos vivos de la ingesta: el bucle del scheduler, el pool JDBC y los
 * clientes HTTP. [close] los libera en `ApplicationStopped`.
 */
private class IngestionRuntime(
    private val scope: CoroutineScope,
    private val dataSource: DataSource,
    private val httpClients: List<HttpClient>,
) : AutoCloseable {
    override fun close() {
        scope.cancel()
        httpClients.forEach(HttpClient::close)
        (dataSource as? AutoCloseable)?.close()
    }
}
