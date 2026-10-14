package es.aviferdev.datopublico.feature.feed.data.remote

import es.aviferdev.datopublico.feature.feed.domain.Deadline
import es.aviferdev.datopublico.feature.feed.domain.HighlightReason
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests del mapeo puro del wire del feed al dominio (FT00035): categorías,
 * motivos, plazo, destacados y campos ausentes con valores seguros.
 *
 * No abren red ni base de datos.
 */
class FeedDtoMapperTest {

    @Test
    fun mapsCategoryReasonsDeadlineAndHighlightCrossReference() {
        val response = FeedResponseDto(
            date = "2026-10-13",
            publications = listOf(
                publication(
                    id = "BOE-A-2026-21002",
                    category = FeedCategoryDto.PUBLIC_EMPLOYMENT_AND_EXAMS,
                    deadline = FeedDeadlineDto(limitDate = "2026-10-30"),
                ),
                publication(id = "BOE-A-2026-21001", category = FeedCategoryDto.NORMATIVE_REGULATION),
            ),
            highlights = listOf(
                FeedHighlightDto(
                    publication = publication(
                        id = "BOE-A-2026-21002",
                        category = FeedCategoryDto.PUBLIC_EMPLOYMENT_AND_EXAMS,
                    ),
                    reasons = listOf(
                        FeedHighlightReasonDto.OPEN_DEADLINE,
                        FeedHighlightReasonDto.THEMATIC_KEYWORD,
                    ),
                ),
            ),
        )

        val feed = response.toDailyFeed()

        assertEquals("2026-10-13", feed.date)
        assertEquals(1, feed.highlights.size)
        assertEquals(
            listOf(HighlightReason.OPEN_DEADLINE, HighlightReason.THEMATIC_KEYWORD),
            feed.highlights.first().highlightReasons,
        )
        val employment = feed.publications.first { it.id == "BOE-A-2026-21002" }
        assertTrue(employment.isHighlighted)
        assertEquals(PublicationCategory.PUBLIC_EMPLOYMENT_AND_EXAMS, employment.category)
        assertEquals(Deadline(limitDate = "2026-10-30"), employment.deadline)
        val normative = feed.publications.first { it.id == "BOE-A-2026-21001" }
        assertFalse(normative.isHighlighted)
        assertTrue(normative.highlightReasons.isEmpty())
        assertEquals(PublicationCategory.NORMATIVE_REGULATION, normative.category)
    }

    @Test
    fun everyCategoryTokenMapsToADistinctDomainCategory() {
        val mapped = FeedCategoryDto.entries.map { token -> token.toDomain() }

        assertEquals(FeedCategoryDto.entries.size, mapped.toSet().size)
    }

    @Test
    fun everyHighlightReasonTokenMapsToADistinctDomainReason() {
        val mapped = FeedHighlightReasonDto.entries.map { token -> token.toDomain() }

        assertEquals(FeedHighlightReasonDto.entries.size, mapped.toSet().size)
    }

    @Test
    fun derivesShortSummaryFromWhatChanges() {
        val dto = publication(
            id = "BOE-A-2026-21001",
            category = FeedCategoryDto.NORMATIVE_REGULATION,
            summary = FeedSummaryDto(whatChanges = "Establece un derecho nuevo."),
        )

        val publication = dto.toPublication(isHighlighted = false, highlightReasons = emptyList())

        assertEquals("Establece un derecho nuevo.", publication.shortSummary)
    }

    @Test
    fun missingOptionalFieldsUseSafeDefaults() {
        val dto = publication(
            id = "BOE-A-2026-21001",
            category = FeedCategoryDto.OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
        )

        val publication = dto.toPublication(isHighlighted = false, highlightReasons = emptyList())

        assertEquals("", publication.shortSummary)
        assertEquals("", publication.officialUrl)
        assertNull(publication.deadline)
    }

    /** Publicación de wire de prueba con valores por defecto seguros. */
    private fun publication(
        id: String,
        category: FeedCategoryDto,
        summary: FeedSummaryDto? = null,
        deadline: FeedDeadlineDto? = null,
    ): FeedPublicationDto = FeedPublicationDto(
        id = id,
        title = "Título de prueba",
        publishedDate = "2026-10-13",
        category = category,
        officialUrl = "",
        summary = summary,
        deadline = deadline,
    )
}
