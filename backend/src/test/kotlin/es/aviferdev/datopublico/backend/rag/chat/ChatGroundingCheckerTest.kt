package es.aviferdev.datopublico.backend.rag.chat

import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests de gate (sin red ni BD) de [ChatGroundingChecker]: afirmación sustentada,
 * afirmación fuera del contexto, cifra fuera del contexto, contexto vacío y
 * afirmación sin términos de contenido (escenario 6 del spec).
 */
class ChatGroundingCheckerTest {

    @Test
    fun `a supported answer is grounded`() {
        assertTrue(ChatGroundingChecker.isSupported("Se aumenta el plazo de solicitud", context()))
    }

    @Test
    fun `an answer outside the context is not grounded`() {
        assertFalse(ChatGroundingChecker.isSupported("Se reduce el impuesto especial", context()))
    }

    @Test
    fun `a figure outside the context is not grounded`() {
        assertFalse(ChatGroundingChecker.isSupported("La cuantía es de 5000 euros", context()))
    }

    @Test
    fun `an answer against an empty context is not grounded`() {
        assertFalse(ChatGroundingChecker.isSupported("Se aumenta el plazo de solicitud", emptyList()))
    }

    @Test
    fun `an answer without content tokens is grounded`() {
        assertTrue(ChatGroundingChecker.isSupported("de la", context()))
    }

    private fun context(): List<FragmentEntity> = listOf(
        FragmentEntity(
            publicationId = "BOE-A-2026-1",
            order = 0,
            reference = "Artículo 1",
            content = CONTEXT_TEXT,
        )
    )

    private companion object {
        const val CONTEXT_TEXT =
            "Se aumenta el plazo de solicitud para las personas beneficiarias con una cuantía de 1000 euros."
    }
}
