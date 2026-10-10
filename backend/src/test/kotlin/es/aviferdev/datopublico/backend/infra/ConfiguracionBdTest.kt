package es.aviferdev.datopublico.backend.infra

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests **sin base de datos** de la configuración de conexión (gate).
 *
 * Verifican la derivación de `POSTGRES_*`, los valores por defecto no sensibles
 * y el fail-fast ante una contraseña ausente. No abren ninguna conexión.
 */
class ConfiguracionBdTest {

    @Test
    fun `deriva la url de POSTGRES con defaults no sensibles`() {
        val config = ConfiguracionBd.fromEnv(mapOf("POSTGRES_PASSWORD" to "secreta"))

        assertEquals("jdbc:postgresql://localhost:5432/datopublico", config.url)
        assertEquals("datopublico", config.usuario)
        assertEquals("secreta", config.password)
    }

    @Test
    fun `usa los overrides de POSTGRES`() {
        val config = ConfiguracionBd.fromEnv(
            mapOf(
                "POSTGRES_HOST" to "db.local",
                "POSTGRES_PORT" to "5544",
                "POSTGRES_DB" to "otra",
                "POSTGRES_USER" to "usuario",
                "POSTGRES_PASSWORD" to "secreta",
            )
        )

        assertEquals("jdbc:postgresql://db.local:5544/otra", config.url)
        assertEquals("usuario", config.usuario)
    }

    @Test
    fun `ignora los valores en blanco y usa los defaults`() {
        val config = ConfiguracionBd.fromEnv(
            mapOf(
                "POSTGRES_HOST" to "  ",
                "POSTGRES_PORT" to "",
                "POSTGRES_DB" to " ",
                "POSTGRES_USER" to "",
                "POSTGRES_PASSWORD" to "secreta",
            )
        )

        assertEquals("jdbc:postgresql://localhost:5432/datopublico", config.url)
        assertEquals("datopublico", config.usuario)
    }

    @Test
    fun `falla sin POSTGRES_PASSWORD`() {
        val error = assertFailsWith<IllegalStateException> {
            ConfiguracionBd.fromEnv(emptyMap())
        }

        assertTrue(error.message?.contains("POSTGRES_PASSWORD") == true)
    }

    @Test
    fun `falla con una contrasena en blanco`() {
        assertFailsWith<IllegalStateException> {
            ConfiguracionBd.fromEnv(mapOf("POSTGRES_PASSWORD" to "   "))
        }
    }

    @Test
    fun `falla con un puerto no numerico`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ConfiguracionBd.fromEnv(
                mapOf("POSTGRES_PORT" to "no-es-un-puerto", "POSTGRES_PASSWORD" to "secreta")
            )
        }

        assertTrue(error.message?.contains("Puerto inválido") == true)
    }

    @Test
    fun `falla con un puerto fuera de rango`() {
        assertFailsWith<IllegalArgumentException> {
            ConfiguracionBd.fromEnv(mapOf("POSTGRES_PORT" to "0", "POSTGRES_PASSWORD" to "secreta"))
        }
        assertFailsWith<IllegalArgumentException> {
            ConfiguracionBd.fromEnv(
                mapOf("POSTGRES_PORT" to "70000", "POSTGRES_PASSWORD" to "secreta")
            )
        }
    }

    @Test
    fun `toString no filtra la contrasena`() {
        val config = ConfiguracionBd.fromEnv(mapOf("POSTGRES_PASSWORD" to "muy-secreta"))

        assertTrue(config.toString().contains("password=***"))
        assertTrue(!config.toString().contains("muy-secreta"))
    }
}
