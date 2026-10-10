package es.aviferdev.datopublico.backend

import es.aviferdev.datopublico.backend.infra.AppConfig
import es.aviferdev.datopublico.backend.plugins.configureMonitoring
import es.aviferdev.datopublico.backend.plugins.configureRouting
import es.aviferdev.datopublico.backend.plugins.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/**
 * Composition root del backend: lee la configuración del entorno y arranca Netty.
 *
 * El arranque no depende de la base de datos ni de servicios externos; los
 * subsistemas con dependencias aún inexistentes se cablearán aquí de forma
 * condicional en features posteriores.
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

/** Módulo Ktor: solo serialización, logging de llamadas y rutas operativas. */
fun Application.module() {
    configureSerialization()
    configureMonitoring()
    configureRouting()
}
