package es.aviferdev.datopublico.model

import es.aviferdev.datopublico.serialization.DatoPublicoJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de gate del **contrato de wire del feed diario** (FT00026) en `:shared`:
 * el *round-trip* de [HighlightDto]/[HighlightReasonDto] y de [ResumenDiarioDto]
 * ampliado, los **tokens `@SerialName`** estables y la **compatibilidad hacia
 * atrás** (un wire sin los campos nuevos sigue decodificando con sus defaults).
 *
 * No usan red ni base de datos.
 */
class FeedContractTest {

    private val publication = PublicacionDto(
        id = "BOE-A-2026-1",
        titulo = "Real Decreto de prueba",
        fechaPublicacion = "2026-10-11",
        organismo = "Ministerio de la Presidencia",
        seccion = SeccionBoeDto.I,
        epigrafe = "Disposiciones generales",
        categoria = CategoriaDto.NORMAS_Y_LEGISLACION,
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1",
        resumen = ResumenDto(
            queCambia = "Cambia algo",
            aQuienAfecta = "A la ciudadanía",
            fuenteOficial = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1",
        ),
        plazo = PlazoDto(fechaLimite = "2026-10-30"),
    )

    @Test
    fun `highlight reason tokens are stable snake case`() {
        val tokens = HighlightReasonDto.entries.associateWith { reason ->
            DatoPublicoJson.encodeToString(HighlightReasonDto.serializer(), reason)
        }

        assertEquals("\"seccion_normativa\"", tokens[HighlightReasonDto.NORMATIVE_SECTION])
        assertEquals("\"rango_normativo\"", tokens[HighlightReasonDto.NORMATIVE_RANK])
        assertEquals("\"categoria_tematica\"", tokens[HighlightReasonDto.THEMATIC_KEYWORD])
        assertEquals("\"plazo_abierto\"", tokens[HighlightReasonDto.OPEN_DEADLINE])
        assertEquals("\"organismo_relevante\"", tokens[HighlightReasonDto.RELEVANT_BODY])
    }

    @Test
    fun highlightRoundTripsWithReasonsAndNoScore() {
        val highlight = HighlightDto(
            publication = publication,
            reasons = listOf(HighlightReasonDto.NORMATIVE_SECTION, HighlightReasonDto.OPEN_DEADLINE),
        )

        val json = DatoPublicoJson.encodeToString(HighlightDto.serializer(), highlight)
        val decoded = DatoPublicoJson.decodeFromString(HighlightDto.serializer(), json)

        assertEquals(highlight, decoded)
        assertTrue(json.contains("\"publicacion\""))
        assertTrue(json.contains("\"motivos\""))
        assertTrue(!json.contains("score"))
    }

    @Test
    fun dailyFeedRoundTripsWithPaginationAndHighlights() {
        val feed = ResumenDiarioDto(
            fecha = "2026-10-11",
            publicaciones = listOf(publication),
            highlights = listOf(HighlightDto(publication, listOf(HighlightReasonDto.NORMATIVE_RANK))),
            page = 2,
            pageSize = 5,
            total = 12,
            totalPages = 3,
        )

        val json = DatoPublicoJson.encodeToString(ResumenDiarioDto.serializer(), feed)
        val decoded = DatoPublicoJson.decodeFromString(ResumenDiarioDto.serializer(), json)

        assertEquals(feed, decoded)
        assertTrue(json.contains("\"destacados\""))
        assertTrue(json.contains("\"pagina\""))
        assertTrue(json.contains("\"tamanoPagina\""))
        assertTrue(json.contains("\"total\""))
        assertTrue(json.contains("\"totalPaginas\""))
    }

    @Test
    fun dailyFeedWithoutNewFieldsDefaultsThem() {
        val decoded = DatoPublicoJson.decodeFromString(
            ResumenDiarioDto.serializer(),
            """{"fecha":"2026-10-11","publicaciones":[]}""",
        )

        assertEquals(emptyList(), decoded.highlights)
        assertEquals(1, decoded.page)
        assertEquals(20, decoded.pageSize)
        assertEquals(0, decoded.total)
        assertEquals(0, decoded.totalPages)
    }
}
