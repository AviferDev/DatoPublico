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
    fun `descarga una publication real del BOE y produce body no vacio`() = runTest {
        if (System.getenv("BOE_LIVE_TEST") != "1") {
            println("BoePublicacionLiveTest omitido: exporta BOE_LIVE_TEST=1 para el test real.")
        } else {
            val httpClient = boeTextHttpClient()
            try {
                val publication = BoePublicationHttpParser(BoeTextHttpClient(httpClient))
                    .parse(entradaReal())

                assertEquals(IDENTIFICADOR, publication.id)
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

    private fun entradaReal(): SummaryEntry = SummaryEntry(
        identifier = IDENTIFICADOR,
        control = "2023/3417",
        title = "Resolución de 21 de diciembre de 2023, de la Subsecretaría",
        publicationDate = "2024-01-02",
        section = BoeSectionDto.II_A,
        issuingBody = "MINISTERIO DE ASUNTOS EXTERIORES, UNIÓN EUROPEA Y COOPERACIÓN",
        heading = null,
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=$IDENTIFICADOR",
        xmlUrl = "https://www.boe.es/diario_boe/xml.php?id=$IDENTIFICADOR",
        pdfUrl = null,
    )

    private companion object {
        /** Publicación real de la Sección II.A, estable y verificada con `curl`. */
        const val IDENTIFICADOR = "BOE-A-2024-87"
    }
}
