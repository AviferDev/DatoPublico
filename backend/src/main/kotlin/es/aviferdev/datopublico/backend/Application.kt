package es.aviferdev.datopublico.backend

import es.aviferdev.datopublico.backend.infra.AppConfig
import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.backend.ingesta.job.IngestaConfig
import es.aviferdev.datopublico.backend.ingesta.job.IngestaJobDiario
import es.aviferdev.datopublico.backend.ingesta.job.IngestaSchedule
import es.aviferdev.datopublico.backend.ingesta.job.IngestaScheduler
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
    configureIngesta()
}

/**
 * Cablea la ingesta de forma **condicional y no bloqueante**.
 *
 * Solo se arranca si `INGESTA_ENABLED` está activo **y** la configuración de base
 * de datos resuelve (`POSTGRES_PASSWORD` presenta). Cualquier fallo al construirla
 * se registra y el servidor sigue arrancando; al parar la aplicación se cancela el
 * bucle y se cierran el pool y los clientes HTTP.
 */
private fun Application.configureIngesta() {
    val config = IngestaConfig.fromEnv()
    if (config.enabled) {
        startIngesta(config)
    } else {
        log.info("Ingesta deshabilitada (INGESTA_ENABLED=false)")
    }
}

/** Arranca la ingesta y programa su cierre; un fallo no impide el arranque. */
private fun Application.startIngesta(config: IngestaConfig) {
    try {
        val runtime = crearIngestaRuntime(config)
        monitor.subscribe(ApplicationStopped) { runtime.close() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        log.warn("Ingesta no arrancada: {}", error.message)
    }
}

/**
 * Construye los recursos de la ingesta (pool JDBC, clientes HTTP, job y
 * scheduler) y lanza el bucle. Si algo falla, cierra lo ya creado y propaga.
 */
private fun Application.crearIngestaRuntime(config: IngestaConfig): IngestaRuntime {
    val dataSource = Database.createDataSource(DatabaseConfig.fromEnv())
    val sumarioHttp = boeSumarioHttpClient()
    val textoHttp = boeTextoHttpClient()
    return try {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scheduler = IngestaScheduler(
            schedule = IngestaSchedule(config.times, config.zone),
            job = IngestaJobDiario(
                sumarioClient = BoeSumarioHttpClient(sumarioHttp),
                publicacionParser = BoePublicacionHttpParser(BoeTextoHttpClient(textoHttp)),
                repositorio = PublicationRepositoryJdbc(dataSource),
            ),
            zone = config.zone,
        )
        scheduler.iniciar(scope)
        log.info("Ingesta programada: horas={} zona={}", config.times, config.zone)
        IngestaRuntime(scope, dataSource, listOf(sumarioHttp, textoHttp))
    } catch (error: Exception) {
        sumarioHttp.close()
        textoHttp.close()
        (dataSource as? AutoCloseable)?.close()
        throw error
    }
}

/**
 * Recursos vivos de la ingesta: el bucle del scheduler, el pool JDBC y los
 * clientes HTTP. [close] los libera en `ApplicationStopped`.
 */
private class IngestaRuntime(
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
