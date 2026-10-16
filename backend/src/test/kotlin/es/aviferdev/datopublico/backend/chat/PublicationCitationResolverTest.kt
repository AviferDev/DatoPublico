package es.aviferdev.datopublico.backend.chat

import es.aviferdev.datopublico.backend.ingestion.publication.Publication
import es.aviferdev.datopublico.backend.persistence.PublicationRepository
import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.BoeSectionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests de gate (sin red ni BD) del primer `CitationResolver` de producción
 * ([PublicationCitationResolver]) sobre un [PublicationRepository] en memoria: una
 * publicación existente se resuelve a su cita con la **URL oficial inyectada** y
 * un identificador desconocido devuelve `null` (no se inventa una fuente).
 */
class PublicationCitationResolverTest {

    @Test
    fun `resolves an existing publication to its official citation`() {
        val resolver = PublicationCitationResolver(InMemoryPublicationRepository(listOf(publication())))

        val citation = resolver.resolve(PUBLICATION_ID)

        assertEquals(PUBLICATION_ID, citation?.publicationId)
        assertEquals("Real Decreto de prueba", citation?.title)
        assertEquals(URL, citation?.officialUrl)
    }

    @Test
    fun `returns null for an unknown publication`() {
        val resolver = PublicationCitationResolver(InMemoryPublicationRepository(listOf(publication())))

        assertNull(resolver.resolve("BOE-A-DESCONOCIDA"))
    }

    private fun publication(): Publication = Publication(
        id = PUBLICATION_ID,
        title = "Real Decreto de prueba",
        publicationDate = "2026-10-11",
        issuingBody = null,
        section = BoeSectionDto.I,
        heading = null,
        body = null,
        officialUrl = URL,
        xmlUrl = null,
        pdfUrl = null,
        rank = "Real Decreto",
        category = CategoryDto.NORMS_AND_LEGISLATION,
    )

    /** Doble en memoria de [PublicationRepository] para el gate. */
    private class InMemoryPublicationRepository(
        private val publications: List<Publication>,
    ) : PublicationRepository {
        override fun save(publication: Publication): Publication = publication

        override fun findById(id: String): Publication? =
            publications.firstOrNull { publication -> publication.id == id }

        override fun listByDate(publicationDate: String): List<Publication> =
            publications.filter { publication -> publication.publicationDate == publicationDate }
    }

    private companion object {
        const val PUBLICATION_ID = "BOE-A-2026-1"
        const val URL = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1"
    }
}
