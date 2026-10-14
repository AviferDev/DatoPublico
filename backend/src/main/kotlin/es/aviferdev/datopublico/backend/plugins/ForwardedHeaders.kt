package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.infra.ProxySettings
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders

/**
 * Instala el plugin **oficial** `XForwardedHeaders` (FT00041) de forma
 * **condicional**.
 *
 * En producción el backend va detrás de Nginx, que termina TLS y añade las
 * cabeceras `X-Forwarded-*`. Con [ProxySettings.trustProxyHeaders] activo se
 * instala el plugin con la política por defecto `useFirstProxy()` (Nginx añade la
 * IP del cliente al **frente** de `X-Forwarded-For`), de modo que
 * `call.request.origin.remoteHost` devuelve la **IP del cliente** y el rate
 * limiting por IP (FT00030) sigue funcionando.
 *
 * Con el flag **apagado** (por defecto) **no** se instala nada: el comportamiento
 * en desarrollo y en el gate no cambia. Confiar en la cabecera solo es seguro si
 * el backend **nunca** publica puerto (lo garantiza el compose de producción);
 * activarlo sin proxy permitiría falsear la IP del rate limiting.
 *
 * @param settings confianza de cabeceras de proxy resuelta del entorno.
 */
fun Application.configureForwardedHeaders(settings: ProxySettings) {
    if (settings.trustProxyHeaders) {
        install(XForwardedHeaders) {
            useFirstProxy()
        }
    }
}
