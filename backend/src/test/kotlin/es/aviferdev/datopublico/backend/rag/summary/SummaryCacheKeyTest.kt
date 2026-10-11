package es.aviferdev.datopublico.backend.rag.summary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * Tests de gate (sin red ni BD) de [SummaryCacheKey] (escenario 5 del spec):
 * determinista, con el formato documentado y **fail-fast** ante componentes en
 * blanco.
 */
class SummaryCacheKeyTest {

    @Test
    fun `is deterministic for the same inputs`() {
        val first = SummaryCacheKey.of("BOE-A-2026-1", "deepseek-v4.1-flash", "v1")
        val second = SummaryCacheKey.of("BOE-A-2026-1", "deepseek-v4.1-flash", "v1")

        assertEquals(first, second)
    }

    @Test
    fun `joins publication model and prompt version with a pipe`() {
        val key = SummaryCacheKey.of("BOE-A-2026-1", "deepseek-v4.1-flash", "v1")

        assertEquals("BOE-A-2026-1|deepseek-v4.1-flash|v1", key)
    }

    @Test
    fun `changes when the model changes`() {
        val key = SummaryCacheKey.of("BOE-A-2026-1", "deepseek-v4.1-flash", "v1")

        assertNotEquals(key, SummaryCacheKey.of("BOE-A-2026-1", "otro-modelo", "v1"))
    }

    @Test
    fun `changes when the prompt version changes`() {
        val key = SummaryCacheKey.of("BOE-A-2026-1", "deepseek-v4.1-flash", "v1")

        assertNotEquals(key, SummaryCacheKey.of("BOE-A-2026-1", "deepseek-v4.1-flash", "v2"))
    }

    @Test
    fun `fails fast on a blank publication id`() {
        assertFailsWith<IllegalArgumentException> {
            SummaryCacheKey.of(" ", "deepseek-v4.1-flash", "v1")
        }
    }

    @Test
    fun `fails fast on a blank model`() {
        assertFailsWith<IllegalArgumentException> {
            SummaryCacheKey.of("BOE-A-2026-1", "", "v1")
        }
    }

    @Test
    fun `fails fast on a blank prompt version`() {
        assertFailsWith<IllegalArgumentException> {
            SummaryCacheKey.of("BOE-A-2026-1", "deepseek-v4.1-flash", " ")
        }
    }
}
