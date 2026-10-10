package es.aviferdev.datopublico.backend.ingesta.publicacion

import es.aviferdev.datopublico.backend.ingesta.sumario.EntradaSumario
import es.aviferdev.datopublico.model.SeccionBoeDto
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
 * BOE_LIVE_TEST=1 ./gradlew :backend:test --tests '*BoePublicacionLiveTest'
 * ```
 */
class BoePublicacionLiveTest {

    @Test
    fun `descarga una publicacion real del BOE y produce texto no vacio`() = runTest {
        if (System.getenv("BOE_LIVE_TEST") != "1") {
            println("BoePublicacionLiveTest omitido: exporta BOE_LIVE_TEST=1 para el test real.")
        } else {
            val httpClient = boeTextoHttpClient()
            try {
                val publicacion = BoePublicacionHttpParser(BoeTextoHttpClient(httpClient))
                    .parsear(entradaReal())

                assertEquals(IDENTIFICADOR, publicacion.id)
                assertEquals("Resolución", publicacion.rango)
                assertTrue(
                    publicacion.texto?.isNotBlank() == true,
                    "la publicación real no trajo texto",
                )
            } finally {
                httpClient.close()
            }
        }
    }

    private fun entradaReal(): EntradaSumario = EntradaSumario(
        identificador = IDENTIFICADOR,
        control = "2023/3417",
        titulo = "Resolución de 21 de diciembre de 2023, de la Subsecretaría",
        fechaPublicacion = "2024-01-02",
        seccion = SeccionBoeDto.II_A,
        organismo = "MINISTERIO DE ASUNTOS EXTERIORES, UNIÓN EUROPEA Y COOPERACIÓN",
        epigrafe = null,
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=$IDENTIFICADOR",
        urlXml = "https://www.boe.es/diario_boe/xml.php?id=$IDENTIFICADOR",
        urlPdf = null,
    )

    private companion object {
        /** Publicación real de la Sección II.A, estable y verificada con `curl`. */
        const val IDENTIFICADOR = "BOE-A-2024-87"
    }
}
