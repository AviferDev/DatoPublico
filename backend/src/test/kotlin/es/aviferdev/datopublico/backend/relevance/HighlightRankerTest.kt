package es.aviferdev.datopublico.backend.relevance

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.PlazoDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ranker de destacados del día (FT00013), sin red ni base de datos.
 *
 * Cubre los escenarios 1–6 del spec: día mixto, prioridad del rango, convocatoria
 * frente a anuncio, organismo de referencia, día sin señales y determinismo frente
 * al orden de entrada. Las publicaciones se construyen en memoria (el ranker es
 * puro); no se inventan fixtures externos.
 */
class HighlightRankerTest {

    private val ranker = HighlightRanker()

    @Test
    fun `mixed day returns the highlighted subset ordered and explained`() {
        val result = ranker.rank(
            listOf(
                notice(id = "BOE-B-2026-5"),
                order(id = "BOE-A-2026-3"),
                publicSectorCall(id = "BOE-A-2026-4"),
                realDecree(id = "BOE-A-2026-2"),
                organicLaw(id = "BOE-A-2026-1"),
            ),
        )

        assertEquals(
            listOf("BOE-A-2026-1", "BOE-A-2026-2", "BOE-A-2026-3", "BOE-A-2026-4"),
            result.map { highlight -> highlight.publication.id },
        )
        assertEquals(6, result[0].score)
        assertEquals(
            listOf(HighlightReason.NORMATIVE_SECTION, HighlightReason.NORMATIVE_RANK),
            result[0].reasons,
        )
        assertEquals(
            listOf(HighlightReason.THEMATIC_KEYWORD, HighlightReason.OPEN_DEADLINE),
            result[3].reasons,
        )
        result.forEach { highlight -> assertTrue(highlight.reasons.isNotEmpty(), highlight.publication.id) }
    }

    @Test
    fun `higher normative rank scores more and comes first`() {
        val result = ranker.rank(
            listOf(
                order(id = "BOE-A-2026-2"),
                organicLaw(id = "BOE-A-2026-1"),
            ),
        )

        assertEquals(listOf("BOE-A-2026-1", "BOE-A-2026-2"), result.map { it.publication.id })
        assertTrue(result[0].score > result[1].score)
        assertTrue(result[0].reasons.contains(HighlightReason.NORMATIVE_RANK))
    }

    @Test
    fun `actionable call is highlighted and trivial notice is not`() {
        val result = ranker.rank(
            listOf(
                publicSectorCall(id = "BOE-A-2026-10"),
                notice(id = "BOE-B-2026-11"),
            ),
        )

        assertEquals(listOf("BOE-A-2026-10"), result.map { it.publication.id })
        assertEquals(
            listOf(HighlightReason.THEMATIC_KEYWORD, HighlightReason.OPEN_DEADLINE),
            result[0].reasons,
        )
        assertFalse(result.any { it.publication.id == "BOE-B-2026-11" })
    }

    @Test
    fun `relevant body adds a modest bonus`() {
        val result = ranker.rank(
            listOf(
                publication(id = "BOE-A-2026-2", rank = "Ley Orgánica", body = "Ayuntamiento de Villanueva"),
                publication(id = "BOE-A-2026-1", rank = "Ley Orgánica", body = "Jefatura del Estado"),
            ),
        )

        assertEquals(listOf("BOE-A-2026-1", "BOE-A-2026-2"), result.map { it.publication.id })
        assertTrue(result[0].reasons.contains(HighlightReason.RELEVANT_BODY))
        assertTrue(result[0].score > result[1].score)
    }

    @Test
    fun `relevant body alone never highlights`() {
        val result = ranker.rank(
            listOf(
                publication(
                    id = "BOE-A-2026-1",
                    section = SeccionBoeDto.V_C,
                    category = CategoriaDto.OTRAS_DISPOSICIONES_Y_ANUNCIOS,
                    body = "Jefatura del Estado",
                ),
            ),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a day without signals returns an empty list`() {
        assertTrue(ranker.rank(emptyList()).isEmpty())
        assertTrue(
            ranker.rank(
                listOf(
                    notice(id = "BOE-B-2026-20"),
                    publication(
                        id = "BOE-B-2026-21",
                        section = SeccionBoeDto.III,
                        category = CategoriaDto.OTRAS_DISPOSICIONES_Y_ANUNCIOS,
                    ),
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun `is deterministic and independent from input order`() {
        val publications = listOf(
            organicLaw(id = "BOE-A-2026-1"),
            realDecree(id = "BOE-A-2026-2"),
            publicSectorCall(id = "BOE-A-2026-3"),
            notice(id = "BOE-B-2026-4"),
        )
        val expected = ranker.rank(publications)
        val permutation = listOf(publications[2], publications[0], publications[3], publications[1])

        assertEquals(expected, ranker.rank(publications))
        assertEquals(expected, ranker.rank(publications.reversed()))
        assertEquals(expected, ranker.rank(permutation))
    }

    @Test
    fun `ties are ordered by identifier ascending`() {
        val result = ranker.rank(
            listOf(
                organicLaw(id = "BOE-A-2026-2"),
                organicLaw(id = "BOE-A-2026-1"),
            ),
        )

        assertEquals(listOf("BOE-A-2026-1", "BOE-A-2026-2"), result.map { it.publication.id })
    }

    @Test
    fun `applies the daily highlight cap`() {
        val publications = (1..15).map { index -> organicLaw(id = "BOE-A-2026-${100 + index}") }

        assertEquals(12, ranker.rank(publications).size)
    }

    /** Publicación de prueba con señales por defecto neutras. */
    private fun publication(
        id: String,
        section: SeccionBoeDto = SeccionBoeDto.I,
        rank: String? = null,
        category: CategoriaDto? = null,
        deadline: PlazoDto? = null,
        body: String? = null,
    ): Publicacion = Publicacion(
        id = id,
        titulo = "Publicación $id",
        fechaPublicacion = PUBLISHED_DATE,
        organismo = body,
        seccion = section,
        epigrafe = null,
        texto = null,
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=$id",
        urlXml = null,
        urlPdf = null,
        rango = rank,
        categoria = category,
        plazo = deadline,
    )

    private fun organicLaw(id: String): Publicacion =
        publication(id = id, section = SeccionBoeDto.I, rank = "Ley Orgánica")

    private fun realDecree(id: String): Publicacion =
        publication(id = id, section = SeccionBoeDto.I, rank = "Real Decreto")

    private fun order(id: String): Publicacion =
        publication(id = id, section = SeccionBoeDto.I, rank = "Orden")

    private fun publicSectorCall(id: String): Publicacion =
        publication(
            id = id,
            section = SeccionBoeDto.II_B,
            category = CategoriaDto.OPOSICIONES_Y_EMPLEO_PUBLICO,
            deadline = DEADLINE,
        )

    private fun notice(id: String): Publicacion =
        publication(
            id = id,
            section = SeccionBoeDto.V_C,
            category = CategoriaDto.OTRAS_DISPOSICIONES_Y_ANUNCIOS,
        )

    private companion object {
        /** Fecha común de la lista del día. */
        const val PUBLISHED_DATE = "2026-10-09"

        /** Plazo de prueba para las convocatorias accionables. */
        val DEADLINE = PlazoDto(fechaLimite = "2026-10-30", descripcion = "plazo de prueba")
    }
}
