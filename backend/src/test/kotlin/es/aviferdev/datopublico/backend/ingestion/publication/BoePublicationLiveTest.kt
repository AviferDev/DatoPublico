package es.aviferdev.datopublico.backend.ingestion.publication

import es.aviferdev.datopublico.backend.ingestion.summary.SummaryEntry
import es.aviferdev.datopublico.model.BoeSectionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Prueba **opt-in** contra el XML real del BOE (escenario 8 del spec).
 *
 * Se salta por defecto (`BOE_LIVE_TEST != 1`) para que el gate y la CI **no**
 * hagan llamadas de red. Para ejecutarla:
 *
 * ```
 * BOE_LIVE_TEST=1 ./gradlew :backend:test --tests '*BoePublicationLiveTest'
 * ```
 */
class BoePublicationLiveTest {

    @Test
    fun `downloads a real BOE publication and produces a non-empty body`() = runTest {
        if (System.getenv("BOE_LIVE_TEST") != "1") {
            println("BoePublicacionLiveTest omitido: exporta BOE_LIVE_TEST=1 para el test real.")
        } else {
            val httpClient = boeTextHttpClient()
            try {
                val publication = BoePublicationHttpParser(BoeTextHttpClient(httpClient))
                    .parse(realEntry())

                assertEquals(IDENTIFIER, publication.id)
                assertEquals("Resolución", publication.rank)
                assertTrue(
                    publication.body?.isNotBlank() == true,
                    "la publicación real no trajo texto",
                )
            } finally {
                httpClient.close()
            }
        }
    }

    private fun realEntry(): SummaryEntry = SummaryEntry(
        identifier = IDENTIFIER,
        control = "2023/3417",
        title = "Resolución de 21 de diciembre de 2023, de la Subsecretaría",
        publicationDate = "2024-01-02",
        section = BoeSectionDto.II_A,
        issuingBody = "MINISTERIO DE ASUNTOS EXTERIORES, UNIÓN EUROPEA Y COOPERACIÓN",
        heading = null,
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=$IDENTIFIER",
        xmlUrl = "https://www.boe.es/diario_boe/xml.php?id=$IDENTIFIER",
        pdfUrl = null,
    )

    private companion object {
        /** Publicación real de la Sección II.A, estable y verificada con `curl`. */
        const val IDENTIFIER = "BOE-A-2024-87"
    }
}
