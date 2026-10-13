package es.aviferdev.datopublico.model

import es.aviferdev.datopublico.serialization.DatoPublicoJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de gate del **contrato de wire de la búsqueda** (FT00028) en `:shared`:
 * el *round-trip* de [SearchResultsDto], los **tokens `@SerialName`** estables
 * (`q` / `resultados`) y el default de la lista vacía.
 *
 * No usan red ni base de datos.
 */
class SearchContractTest {

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
    fun searchResultsUsesStableWireTokens() {
        val results = SearchResultsDto(query = "becas para jóvenes", results = listOf(publication))

        val json = DatoPublicoJson.encodeToString(SearchResultsDto.serializer(), results)

        assertTrue(json.contains("\"q\""))
        assertTrue(json.contains("\"resultados\""))
        assertTrue(!json.contains("query"))
        assertTrue(!json.contains("results"))
    }

    @Test
    fun searchResultsRoundTripsWithReusedPublicationDto() {
        val results = SearchResultsDto(query = "oposiciones de sanidad", results = listOf(publication))

        val json = DatoPublicoJson.encodeToString(SearchResultsDto.serializer(), results)
        val decoded = DatoPublicoJson.decodeFromString(SearchResultsDto.serializer(), json)

        assertEquals(results, decoded)
        assertEquals(publication, decoded.results.first())
    }

    @Test
    fun searchResultsWithoutResultsDefaultsToEmptyList() {
        val decoded = DatoPublicoJson.decodeFromString(
            SearchResultsDto.serializer(),
            """{"q":"becas"}""",
        )

        assertEquals("becas", decoded.query)
        assertEquals(emptyList(), decoded.results)
    }
}
