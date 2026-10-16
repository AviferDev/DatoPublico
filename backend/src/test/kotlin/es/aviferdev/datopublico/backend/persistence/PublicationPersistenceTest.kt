package es.aviferdev.datopublico.backend.persistence

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.DeadlineDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests **sin base de datos** del mapeo dominio ↔ entidad de publicación (gate).
 *
 * Cubren el viaje de ida y vuelta con metadatos completos (categoría, sección,
 * epígrafe y plazo) y el caso con opcionales nulos (parser de FT00007 antes de
 * FT00012). No abren ninguna conexión.
 */
class PublicationPersistenceTest {

    @Test
    fun `maps a full publication round trip`() {
        val publication = fullPublication()

        val retrieved = publication.toPublicationEntity().toDomain()

        assertEquals(publication, retrieved)
    }

    @Test
    fun `stores section and category by enum name`() {
        val entity = fullPublication().toPublicationEntity()

        assertEquals("II_A", entity.section)
        assertEquals("BECAS_SUBVENCIONES_Y_AYUDAS", entity.category)
    }

    @Test
    fun `maps the deadline into its two columns`() {
        val entity = fullPublication().toPublicationEntity()

        assertEquals("2026-11-30", entity.deadlineDate)
        assertEquals("Diez días hábiles", entity.deadlineDescription)
    }

    @Test
    fun `keeps the nulls from the parser before FT00012`() {
        val publication = Publicacion(
            id = "BOE-A-2024-87",
            title = "Resolución de 21 de diciembre de 2023",
            publicationDate = "2024-01-02",
            issuingBody = null,
            section = SeccionBoeDto.II_A,
            heading = null,
            texto = null,
            officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2024-87",
            urlXml = null,
            urlPdf = null,
            rango = null,
        )

        val entity = publication.toPublicationEntity()
        val retrieved = entity.toDomain()

        assertNull(entity.category)
        assertNull(entity.deadlineDate)
        assertNull(entity.deadlineDescription)
        assertNull(retrieved.issuingBody)
        assertNull(retrieved.heading)
        assertNull(retrieved.texto)
        assertNull(retrieved.category)
        assertNull(retrieved.deadline)
        assertEquals(publication, retrieved)
    }

    @Test
    fun `keeps a deadline without description`() {
        val publication = fullPublication().copy(
            deadline = DeadlineDto(deadlineDate = "2026-12-15"),
        )

        assertEquals(publication.deadline, publication.toPublicationEntity().toDomain().deadline)
    }

    private fun fullPublication(): Publicacion = Publicacion(
        id = "BOE-A-2026-20979",
        title = "Resolución de 8 de octubre de 2026",
        publicationDate = "2026-10-09",
        issuingBody = "MINISTERIO DE EDUCACIÓN",
        section = SeccionBoeDto.II_A,
        heading = "Becas y subvenciones",
        texto = "Primero. Convocar...\nSegundo. El plazo...",
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-20979",
        urlXml = "https://www.boe.es/diario_boe/xml.php?id=BOE-A-2026-20979",
        urlPdf = "https://www.boe.es/boe/dias/2026/10/09/pdfs/A00001-00002.pdf",
        rango = "Resolución",
        category = CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS,
        deadline = DeadlineDto(deadlineDate = "2026-11-30", description = "Diez días hábiles"),
    )
}
