package es.aviferdev.datopublico.backend.infra

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

/**
 * Tests de gate de la **configuración de endurecimiento HTTP** (FT00030) en
 * `:backend`, sin red ni base de datos: valores por defecto, *overrides* por
 * entorno, normalización de la allowlist de CORS y *fail-fast* de los valores
 * inválidos.
 */
class HttpHardeningConfigTest {

    @Test
    fun `cors defaults to any origin`() {
        assertEquals(CorsSettings(emptyList()), CorsSettings.fromEnv(emptyMap()))
        assertEquals(CorsSettings(emptyList()), CorsSettings.default())
    }

    @Test
    fun `cors parses and normalizes the allowed origins`() {
        val settings = CorsSettings.fromEnv(
            mapOf("CORS_ALLOWED_ORIGINS" to " https://a.example , ,https://b.example ")
        )

        assertEquals(listOf("https://a.example", "https://b.example"), settings.allowedOrigins)
    }

    @Test
    fun `cors with a blank variable is an empty allowlist`() {
        assertEquals(
            emptyList(),
            CorsSettings.fromEnv(mapOf("CORS_ALLOWED_ORIGINS" to "   ")).allowedOrigins,
        )
    }

    @Test
    fun `rate limit defaults to the general and chat quotas`() {
        val defaults = RateLimitSettings.fromEnv(emptyMap())

        assertEquals(RateLimitSettings.default(), defaults)
        assertEquals(RateLimitSettings.DEFAULT_REQUESTS, defaults.requests)
        assertEquals(RateLimitSettings.DEFAULT_WINDOW_SECONDS.seconds, defaults.window)
        assertEquals(RateLimitSettings.DEFAULT_CHAT_REQUESTS, defaults.chatRequests)
        assertEquals(RateLimitSettings.DEFAULT_CHAT_WINDOW_SECONDS.seconds, defaults.chatWindow)
    }

    @Test
    fun `rate limit reads the overrides from the environment`() {
        val settings = RateLimitSettings.fromEnv(
            mapOf(
                "RATE_LIMIT_REQUESTS" to "100",
                "RATE_LIMIT_WINDOW_SECONDS" to "30",
                "CHAT_RATE_LIMIT_REQUESTS" to "3",
                "CHAT_RATE_LIMIT_WINDOW_SECONDS" to "15",
            )
        )

        assertEquals(100, settings.requests)
        assertEquals(30.seconds, settings.window)
        assertEquals(3, settings.chatRequests)
        assertEquals(15.seconds, settings.chatWindow)
    }

    @Test
    fun `rate limit fails fast on invalid values`() {
        assertFailsWith<IllegalArgumentException> {
            RateLimitSettings.fromEnv(mapOf("RATE_LIMIT_REQUESTS" to "abc"))
        }
        assertFailsWith<IllegalArgumentException> {
            RateLimitSettings.fromEnv(mapOf("RATE_LIMIT_WINDOW_SECONDS" to "0"))
        }
        assertFailsWith<IllegalArgumentException> {
            RateLimitSettings.fromEnv(mapOf("CHAT_RATE_LIMIT_REQUESTS" to "-1"))
        }
        assertFailsWith<IllegalArgumentException> {
            RateLimitSettings.fromEnv(mapOf("CHAT_RATE_LIMIT_WINDOW_SECONDS" to "1.5"))
        }
    }

    @Test
    fun `proxy headers default to not trusting`() {
        assertEquals(ProxySettings(false), ProxySettings.default())
        assertEquals(ProxySettings(false), ProxySettings.fromEnv(emptyMap()))
        assertEquals(ProxySettings(false), ProxySettings.fromEnv(mapOf("TRUST_PROXY_HEADERS" to "false")))
        assertEquals(ProxySettings(false), ProxySettings.fromEnv(mapOf("TRUST_PROXY_HEADERS" to "  ")))
    }

    @Test
    fun `proxy headers read the override from the environment`() {
        assertEquals(ProxySettings(true), ProxySettings.fromEnv(mapOf("TRUST_PROXY_HEADERS" to "true")))
        assertEquals(ProxySettings(true), ProxySettings.fromEnv(mapOf("TRUST_PROXY_HEADERS" to "TRUE")))
    }

    @Test
    fun `proxy headers fail fast on invalid values`() {
        assertFailsWith<IllegalArgumentException> {
            ProxySettings.fromEnv(mapOf("TRUST_PROXY_HEADERS" to "yes"))
        }
    }
}
