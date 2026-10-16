package es.aviferdev.datopublico.backend.feed

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.persistence.SummaryEntity
import es.aviferdev.datopublico.backend.relevance.Highlight
import es.aviferdev.datopublico.backend.relevance.HighlightReason
import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.HighlightReasonDto
import es.aviferdev.datopublico.model.DeadlineDto
import es.aviferdev.datopublico.model.BoeSectionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests de gate (puro, sin red ni BD) de [FeedDtoMapper]: mapeo del resumen, de la
 * publicación (con categoría/enlace/plazo), de los destacados y traducción
 * exhaustiva de los motivos al enum de wire. Comprueba que **no** se expone el
 * `score` (el DTO no tiene ese campo).
 */
class FeedDtoMapperTest {

    @Test
    fun `a publication is projected with its summary category url and deadline`() {
        val dto = FeedDtoMapper.toPublicacionDto(publication(), summary())

        assertEquals(PUBLICATION_ID, dto.id)
        assertEquals(CategoryDto.NORMS_AND_LEGISLATION, dto.category)
        assertEquals(URL, dto.officialUrl)
        assertEquals(DeadlineDto("2026-10-30"), dto.deadline)
        assertEquals("Cambia algo", dto.summary?.whatChanges)
        assertEquals(URL, dto.summary?.officialSource)
        assertTrue(dto.summary?.aiNotice == true)
    }

    @Test
    fun `the summary is projected without deadline because the table does not store it`() {
        val dto = FeedDtoMapper.toResumenDto(summary())

        assertEquals("Cambia algo", dto.whatChanges)
        assertEquals("A la ciudadanía", dto.whoIsAffected)
        assertEquals(listOf("1 ley"), dto.keyFigures)
        assertEquals(URL, dto.officialSource)
        assertTrue(dto.aiNotice)
        assertNull(dto.deadline)
    }

    @Test
    fun `a publication without category fails fast as corrupt data`() {
        assertFailsWith<IllegalArgumentException> {
            FeedDtoMapper.toPublicacionDto(publication().copy(category = null), summary())
        }
    }

    @Test
    fun `every internal reason maps to its wire token`() {
        assertEquals(
            HighlightReasonDto.NORMATIVE_SECTION,
            FeedDtoMapper.toHighlightReasonDto(HighlightReason.NORMATIVE_SECTION),
        )
        assertEquals(
            HighlightReasonDto.NORMATIVE_RANK,
            FeedDtoMapper.toHighlightReasonDto(HighlightReason.NORMATIVE_RANK),
        )
        assertEquals(
            HighlightReasonDto.THEMATIC_KEYWORD,
            FeedDtoMapper.toHighlightReasonDto(HighlightReason.THEMATIC_KEYWORD),
        )
        assertEquals(
            HighlightReasonDto.OPEN_DEADLINE,
            FeedDtoMapper.toHighlightReasonDto(HighlightReason.OPEN_DEADLINE),
        )
        assertEquals(
            HighlightReasonDto.RELEVANT_BODY,
            FeedDtoMapper.toHighlightReasonDto(HighlightReason.RELEVANT_BODY),
        )
    }

    @Test
    fun `a highlight is projected with its reasons and the full publication`() {
        val dto = FeedDtoMapper.toHighlightDto(
            Highlight(
                publication = publication(),
                score = 6,
                reasons = listOf(HighlightReason.NORMATIVE_SECTION, HighlightReason.OPEN_DEADLINE),
            ),
            summary(),
        )

        assertEquals(PUBLICATION_ID, dto.publication.id)
        assertEquals(
            listOf(HighlightReasonDto.NORMATIVE_SECTION, HighlightReasonDto.OPEN_DEADLINE),
            dto.reasons,
        )
        assertEquals("Cambia algo", dto.publication.summary?.whatChanges)
    }

    private fun publication(): Publicacion = Publicacion(
        id = PUBLICATION_ID,
        title = "Ley de prueba",
        publicationDate = DATE,
        issuingBody = "Jefatura del Estado",
        section = BoeSectionDto.I,
        heading = "Disposiciones generales",
        texto = null,
        officialUrl = URL,
        urlXml = null,
        urlPdf = null,
        rango = "Ley",
        category = CategoryDto.NORMS_AND_LEGISLATION,
        deadline = DeadlineDto("2026-10-30"),
    )

    private fun summary(): SummaryEntity = SummaryEntity(
        id = 1L,
        publicationId = PUBLICATION_ID,
        whatChanges = "Cambia algo",
        whomItAffects = "A la ciudadanía",
        keyFigures = listOf("1 ley"),
        officialSource = URL,
        status = "publicado",
    )

    private companion object {
        const val DATE = "2026-10-11"
        const val PUBLICATION_ID = "BOE-A-2026-1"
        const val URL = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1"
    }
}
