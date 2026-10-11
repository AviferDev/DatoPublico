package es.aviferdev.datopublico.backend.rag.guardrails

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests de gate (sin red ni BD) de [OfficialSourceValidator]: https, host del BOE
 * e igualdad con la publicación (escenario 4 del spec).
 */
class OfficialSourceValidatorTest {

    private val expected = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1"

    @Test
    fun `accepts an official https boe source identical to the publication`() {
        assertTrue(OfficialSourceValidator.isOfficial(expected, expected))
    }

    @Test
    fun `accepts the boe es host without www`() {
        val source = "https://boe.es/diario_boe/txt.php?id=BOE-A-2026-1"

        assertTrue(OfficialSourceValidator.isOfficial(source, source))
    }

    @Test
    fun `rejects a blank source`() {
        assertFalse(OfficialSourceValidator.isOfficial("   ", expected))
    }

    @Test
    fun `rejects a non https source`() {
        assertFalse(OfficialSourceValidator.isOfficial(expected.replace("https://", "http://"), expected))
    }

    @Test
    fun `rejects a foreign host`() {
        val source = "https://example.com/diario_boe/txt.php?id=BOE-A-2026-1"

        assertFalse(OfficialSourceValidator.isOfficial(source, source))
    }

    @Test
    fun `rejects a source different from the publication`() {
        val other = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-2"

        assertFalse(OfficialSourceValidator.isOfficial(other, expected))
    }

    @Test
    fun `rejects a lookalike host`() {
        val source = "https://www.boe.es.evil.com/diario_boe/txt.php?id=BOE-A-2026-1"

        assertFalse(OfficialSourceValidator.isOfficial(source, source))
    }
}
