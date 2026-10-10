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
class DatabaseConfigTest {

    @Test
    fun `derives the url from POSTGRES with non sensitive defaults`() {
        val config = DatabaseConfig.fromEnv(mapOf("POSTGRES_PASSWORD" to "secreta"))

        assertEquals("jdbc:postgresql://localhost:5432/datopublico", config.url)
        assertEquals("datopublico", config.user)
        assertEquals("secreta", config.password)
    }

    @Test
    fun `uses the POSTGRES overrides`() {
        val config = DatabaseConfig.fromEnv(
            mapOf(
                "POSTGRES_HOST" to "db.local",
                "POSTGRES_PORT" to "5544",
                "POSTGRES_DB" to "otra",
                "POSTGRES_USER" to "usuario",
                "POSTGRES_PASSWORD" to "secreta",
            )
        )

        assertEquals("jdbc:postgresql://db.local:5544/otra", config.url)
        assertEquals("usuario", config.user)
    }

    @Test
    fun `ignores blank values and uses the defaults`() {
        val config = DatabaseConfig.fromEnv(
            mapOf(
                "POSTGRES_HOST" to "  ",
                "POSTGRES_PORT" to "",
                "POSTGRES_DB" to " ",
                "POSTGRES_USER" to "",
                "POSTGRES_PASSWORD" to "secreta",
            )
        )

        assertEquals("jdbc:postgresql://localhost:5432/datopublico", config.url)
        assertEquals("datopublico", config.user)
    }

    @Test
    fun `fails without POSTGRES_PASSWORD`() {
        val error = assertFailsWith<IllegalStateException> {
            DatabaseConfig.fromEnv(emptyMap())
        }

        assertTrue(error.message?.contains("POSTGRES_PASSWORD") == true)
    }

    @Test
    fun `fails with a blank password`() {
        assertFailsWith<IllegalStateException> {
            DatabaseConfig.fromEnv(mapOf("POSTGRES_PASSWORD" to "   "))
        }
    }

    @Test
    fun `fails with a non numeric port`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DatabaseConfig.fromEnv(
                mapOf("POSTGRES_PORT" to "no-es-un-puerto", "POSTGRES_PASSWORD" to "secreta")
            )
        }

        assertTrue(error.message?.contains("Puerto inválido") == true)
    }

    @Test
    fun `fails with an out of range port`() {
        assertFailsWith<IllegalArgumentException> {
            DatabaseConfig.fromEnv(mapOf("POSTGRES_PORT" to "0", "POSTGRES_PASSWORD" to "secreta"))
        }
        assertFailsWith<IllegalArgumentException> {
            DatabaseConfig.fromEnv(
                mapOf("POSTGRES_PORT" to "70000", "POSTGRES_PASSWORD" to "secreta")
            )
        }
    }

    @Test
    fun `toString does not leak the password`() {
        val config = DatabaseConfig.fromEnv(mapOf("POSTGRES_PASSWORD" to "muy-secreta"))

        assertTrue(config.toString().contains("password=***"))
        assertTrue(!config.toString().contains("muy-secreta"))
    }
}
