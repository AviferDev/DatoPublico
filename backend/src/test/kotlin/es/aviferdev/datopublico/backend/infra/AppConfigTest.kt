package es.aviferdev.datopublico.backend.infra

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppConfigTest {

    @Test
    fun `usa valores por defecto sin entorno`() {
        val config = AppConfig.fromEnv(emptyMap())

        assertEquals("0.0.0.0", config.host)
        assertEquals(8080, config.port)
        assertEquals("local", config.environment)
        assertEquals("INFO", config.logLevel)
    }

    @Test
    fun `lee los overrides del entorno`() {
        val config = AppConfig.fromEnv(
            mapOf(
                "SERVER_HOST" to "127.0.0.1",
                "SERVER_PORT" to "8090",
                "APP_ENV" to "staging",
                "LOG_LEVEL" to "DEBUG",
            )
        )

        assertEquals("127.0.0.1", config.host)
        assertEquals(8090, config.port)
        assertEquals("staging", config.environment)
        assertEquals("DEBUG", config.logLevel)
    }

    @Test
    fun `acepta PORT como alternativa a SERVER_PORT`() {
        val config = AppConfig.fromEnv(mapOf("PORT" to "9090"))

        assertEquals(9090, config.port)
    }

    @Test
    fun `ignora valores en blanco y usa los defaults`() {
        val config = AppConfig.fromEnv(mapOf("SERVER_HOST" to "  ", "SERVER_PORT" to "", "APP_ENV" to " "))

        assertEquals("0.0.0.0", config.host)
        assertEquals(8080, config.port)
        assertEquals("local", config.environment)
    }

    @Test
    fun `falla con un puerto no numerico`() {
        val error = assertFailsWith<IllegalArgumentException> {
            AppConfig.fromEnv(mapOf("SERVER_PORT" to "no-es-un-numero"))
        }

        assertEquals(true, error.message?.contains("Puerto inválido"))
    }

    @Test
    fun `falla con un puerto fuera de rango`() {
        assertFailsWith<IllegalArgumentException> { AppConfig.fromEnv(mapOf("SERVER_PORT" to "0")) }
        assertFailsWith<IllegalArgumentException> { AppConfig.fromEnv(mapOf("SERVER_PORT" to "70000")) }
    }
}
