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

    private val publication = PublicationDto(
        id = "BOE-A-2026-1",
        title = "Real Decreto de prueba",
        publicationDate = "2026-10-11",
        issuingBody = "Ministerio de la Presidencia",
        section = BoeSectionDto.I,
        heading = "Disposiciones generales",
        category = CategoryDto.NORMS_AND_LEGISLATION,
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1",
        summary = SummaryDto(
            whatChanges = "Cambia algo",
            whoIsAffected = "A la ciudadanía",
            officialSource = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1",
        ),
        deadline = DeadlineDto(deadlineDate = "2026-10-30"),
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
