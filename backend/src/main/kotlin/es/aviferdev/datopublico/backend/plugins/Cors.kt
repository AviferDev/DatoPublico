package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.infra.CorsSettings
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

/**
 * Configura **CORS** (FT00030) para que el futuro cliente Web (Wasm) pueda llamar
 * a la API desde otro origen, incluido el *preflight* de `POST /v1/chat` con
 * `Content-Type: application/json`.
 *
 * El plugin oficial `CORS` de Ktor resuelve el *preflight* (`OPTIONS`, `200`) y
 * añade las cabeceras `Access-Control-*` a las peticiones reales; hacerlo a mano
 * sería propenso a errores. Se instala a nivel de **aplicación**, así que afecta a
 * todas las rutas (incluidas `/health` y `/openapi.json`).
 *
 * Política de orígenes: con [CorsSettings.allowedOrigins] **vacía** se permite
 * cualquier origen (`anyHost()`) porque la API es pública, de solo lectura y **sin
 * credenciales** (no hay datos de usuario que proteger); con una allowlist, solo se
 * permiten los orígenes listados. `allowCredentials` es siempre `false` (no hay
 * cookies ni sesión).
 *
 * @param settings configuración de orígenes permitidos.
 */
fun Application.configureCors(settings: CorsSettings) {
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowNonSimpleContentTypes = true
        allowCredentials = false
        if (settings.allowedOrigins.isEmpty()) {
            anyHost()
        } else {
            allowOrigins { origin -> origin in settings.allowedOrigins }
        }
    }
}
