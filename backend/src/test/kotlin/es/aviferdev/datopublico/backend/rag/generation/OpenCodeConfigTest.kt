package es.aviferdev.datopublico.backend.rag.generation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests de gate (sin red) de [OpenCodeConfig]: resolución de `OPENCODE_*` desde el
 * entorno con **fail-fast** de la clave (escenario 7 del spec) y valores por
 * defecto.
 */
class OpenCodeConfigTest {

    @Test
    fun `resolves the api key and the default base url model and temperature`() {
        val config = OpenCodeConfig.fromEnv(mapOf(OpenCodeConfig.API_KEY_ENV to "clave-secreta"))

        assertEquals("clave-secreta", config.apiKey)
        assertEquals(OpenCodeConfig.DEFAULT_BASE_URL, config.baseUrl)
        assertEquals(OpenCodeConfig.DEFAULT_MODEL, config.model)
        assertEquals(OpenCodeConfig.DEFAULT_TEMPERATURE, config.temperature)
    }

    @Test
    fun `accepts configured base url model and temperature`() {
        val config = OpenCodeConfig.fromEnv(
            mapOf(
                OpenCodeConfig.API_KEY_ENV to "clave",
                OpenCodeConfig.BASE_URL_ENV to "https://ejemplo.test/v1",
                OpenCodeConfig.MODEL_ENV to "otro-modelo",
                OpenCodeConfig.TEMPERATURE_ENV to "0.7",
            )
        )

        assertEquals("https://ejemplo.test/v1", config.baseUrl)
        assertEquals("otro-modelo", config.model)
        assertEquals(0.7, config.temperature)
    }

    @Test
    fun `fails fast when the api key is missing`() {
        val error = assertFailsWith<TextGenerationException> { OpenCodeConfig.fromEnv(emptyMap()) }

        assertTrue(error.message.orEmpty().contains(OpenCodeConfig.API_KEY_ENV))
    }

    @Test
    fun `fails fast when the api key is blank`() {
        val error = assertFailsWith<TextGenerationException> {
            OpenCodeConfig.fromEnv(mapOf(OpenCodeConfig.API_KEY_ENV to "   "))
        }

        assertTrue(error.message.orEmpty().contains(OpenCodeConfig.API_KEY_ENV))
    }

    @Test
    fun `fails fast when the temperature is not a finite non negative number`() {
        val error = assertFailsWith<TextGenerationException> {
            OpenCodeConfig.fromEnv(
                mapOf(
                    OpenCodeConfig.API_KEY_ENV to "clave",
                    OpenCodeConfig.TEMPERATURE_ENV to "caliente",
                )
            )
        }

        assertTrue(error.message.orEmpty().contains(OpenCodeConfig.TEMPERATURE_ENV))
    }
}
