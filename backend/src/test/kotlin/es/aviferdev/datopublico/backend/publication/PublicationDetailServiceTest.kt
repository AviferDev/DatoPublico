package es.aviferdev.datopublico.backend.publication

import es.aviferdev.datopublico.backend.ingesta.publication.Publicacion
import es.aviferdev.datopublico.backend.persistence.PublicationRepository
import es.aviferdev.datopublico.backend.persistence.SummaryEntity
import es.aviferdev.datopublico.backend.persistence.SummaryRepository
import es.aviferdev.datopublico.backend.rag.review.SummaryState
import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.DeadlineDto
import es.aviferdev.datopublico.model.BoeSectionDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests de gate (sin red ni BD) de [PublicationDetailService] con repositorios en
 * memoria (escenarios 1–3 y 6 del spec): publicado → DTO completo, resumen no
 * publicado → `null`, sin resumen → `null`, publicación inexistente → `null`,
 * categoría nula (dato corrupto) → `null` y determinismo/trazabilidad.
 */
class PublicationDetailServiceTest {

    @Test
    fun `a published summary is served as a full publication dto`() = runTest {
        val service = service(
            publications = listOf(publication(PUBLISHED_ID)),
            summaries = listOf(summary(PUBLISHED_ID, SummaryState.PUBLISHED.persistedValue)),
        )

        val detail = service.detail(PUBLISHED_ID)

        assertNotNull(detail)
        assertEquals(PUBLISHED_ID, detail.id)
        assertEquals("Convocatoria de prueba", detail.title)
        assertEquals(DATE, detail.publicationDate)
        assertEquals("Ministerio de prueba", detail.issuingBody)
        assertEquals(BoeSectionDto.II_B, detail.section)
        assertEquals("Becas y ayudas", detail.heading)
        assertEquals(CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID, detail.category)
        assertEquals("https://www.boe.es/diario_boe/txt.php?id=$PUBLISHED_ID", detail.officialUrl)
        assertEquals(DeadlineDto("2026-10-30", "20 días hábiles"), detail.deadline)
        val summary = detail.summary
        assertNotNull(summary)
        assertEquals("Cambia algo en $PUBLISHED_ID", summary.whatChanges)
        assertEquals("A la ciudadanía", summary.whoIsAffected)
        assertEquals(listOf("20 días"), summary.keyFigures)
        assertEquals("https://www.boe.es/diario_boe/txt.php?id=$PUBLISHED_ID", summary.officialSource)
        assertTrue(summary.aiNotice)
    }

    @Test
    fun `a summary that is not published is not served`() = runTest {
        val states = listOf(
            SummaryState.IN_REVIEW,
            SummaryState.PENDING,
            SummaryState.GENERATED,
            SummaryState.VALIDATED,
            SummaryState.DISCARDED,
        )

        states.forEach { state ->
            val service = service(
                publications = listOf(publication(PUBLISHED_ID)),
                summaries = listOf(summary(PUBLISHED_ID, state.persistedValue)),
            )

            assertNull(service.detail(PUBLISHED_ID), "el estado ${state.persistedValue} no debe servirse")
        }
    }

    @Test
    fun `a publication without summary is not served`() = runTest {
        val service = service(
            publications = listOf(publication(PUBLISHED_ID)),
            summaries = emptyList(),
        )

        assertNull(service.detail(PUBLISHED_ID))
    }

    @Test
    fun `an unknown publication is not served`() = runTest {
        val service = service(
            publications = listOf(publication(PUBLISHED_ID)),
            summaries = listOf(summary(PUBLISHED_ID, SummaryState.PUBLISHED.persistedValue)),
        )

        assertNull(service.detail("BOE-A-DESCONOCIDA"))
    }

    @Test
    fun `a publication without category is treated as corrupted and not served`() = runTest {
        val service = service(
            publications = listOf(publication(PUBLISHED_ID, category = null)),
            summaries = listOf(summary(PUBLISHED_ID, SummaryState.PUBLISHED.persistedValue)),
        )

        assertNull(service.detail(PUBLISHED_ID))
    }

    @Test
    fun `the detail is deterministic and keeps source traceability`() = runTest {
        val service = service(
            publications = listOf(publication(PUBLISHED_ID)),
            summaries = listOf(summary(PUBLISHED_ID, SummaryState.PUBLISHED.persistedValue)),
        )

        val first = service.detail(PUBLISHED_ID)
        val second = service.detail(PUBLISHED_ID)

        assertEquals(first, second)
        assertNotNull(first)
        assertTrue(first.officialUrl.startsWith("https://www.boe.es"))
        assertEquals(first.officialUrl, first.summary?.officialSource)
        assertTrue(first.summary?.aiNotice == true)
    }

    private fun service(
        publications: List<Publicacion>,
        summaries: List<SummaryEntity>,
    ): PublicationDetailService = PublicationDetailService(
        InMemoryPublicationRepository(publications),
        InMemorySummaryRepository(summaries),
    )

    private fun publication(
        id: String,
        category: CategoryDto? = CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID,
    ): Publicacion = Publicacion(
        id = id,
        title = "Convocatoria de prueba",
        publicationDate = DATE,
        issuingBody = "Ministerio de prueba",
        section = BoeSectionDto.II_B,
        heading = "Becas y ayudas",
        texto = null,
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=$id",
        urlXml = null,
        urlPdf = null,
        rango = "Resolución",
        category = category,
        deadline = DeadlineDto("2026-10-30", "20 días hábiles"),
    )

    private fun summary(publicationId: String, status: String): SummaryEntity = SummaryEntity(
        publicationId = publicationId,
        whatChanges = "Cambia algo en $publicationId",
        whomItAffects = "A la ciudadanía",
        keyFigures = listOf("20 días"),
        officialSource = "https://www.boe.es/diario_boe/txt.php?id=$publicationId",
        aiNotice = true,
        status = status,
    )

    /** Doble en memoria de [PublicationRepository] para el gate. */
    private class InMemoryPublicationRepository(
        private val publications: List<Publicacion>,
    ) : PublicationRepository {
        override fun save(publication: Publicacion): Publicacion = publication

        override fun findById(id: String): Publicacion? =
            publications.firstOrNull { publication -> publication.id == id }

        override fun listByDate(publicationDate: String): List<Publicacion> =
            publications.filter { publication -> publication.publicationDate == publicationDate }
    }

    /** Doble en memoria de [SummaryRepository] para el gate. */
    private class InMemorySummaryRepository(
        private val summaries: List<SummaryEntity>,
    ) : SummaryRepository {
        override fun save(summary: SummaryEntity): SummaryEntity = summary

        override fun findByPublication(publicationId: String): SummaryEntity? =
            summaries.firstOrNull { summary -> summary.publicationId == publicationId }
    }

    private companion object {
        const val DATE = "2026-10-11"
        const val PUBLISHED_ID = "BOE-A-2026-20979"
    }
}
