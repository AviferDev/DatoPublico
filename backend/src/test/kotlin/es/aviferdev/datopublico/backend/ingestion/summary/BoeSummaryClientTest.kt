package es.aviferdev.datopublico.backend.ingestion.summary

import es.aviferdev.datopublico.model.BoeSectionDto
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Cliente del sumario con [MockEngine] (sin red): cubre los escenarios 1–5 del
 * spec — URL y cabecera, inclusión de todas las secciones, `item` objeto/array,
 * `404` y errores tipados.
 */
class BoeSummaryClientTest {

    private val date = LocalDate.of(2026, 10, 9)

    @Test
    fun `pide GET YYYYMMDD con Accept application json e incluye todas las secciones`() = runTest {
        var capturada: HttpRequestData? = null
        val client = clientCon(MockEngine { request ->
            capturada = request
            respond(
                content = fixture("sumario-20261009.json"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })

        val entries = BoeSummaryHttpClient(client, baseUrl = "https://www.boe.es/datosabiertos/api")
            .fetchSummary(date)

        val request = checkNotNull(capturada)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals(
            "https://www.boe.es/datosabiertos/api/boe/sumario/20261009",
            request.url.toString(),
        )
        assertEquals("application/json", request.headers[HttpHeaders.Accept])
        assertEquals(237, entries.size)
        assertEquals(
            setOf(
                BoeSectionDto.I,
                BoeSectionDto.II_A,
                BoeSectionDto.II_B,
                BoeSectionDto.III,
                BoeSectionDto.V_A,
                BoeSectionDto.V_B,
            ),
            entries.map { it.section }.toSet(),
        )
        assertTrue(entries.any { it.identifier == "BOE-B-2026-32742" }) // Sección V.A (32 en la fuente)
    }

    @Test
    fun `item como objeto o como array produce las mismas entries`() = runTest {
        val entry = SummaryEntry(
            identifier = "BOE-A-1",
            control = "2026/1",
            title = "T",
            publicationDate = "2026-10-09",
            section = BoeSectionDto.I,
            issuingBody = "MINISTERIO X",
            heading = "Epi",
            officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-1",
            urlXml = "https://www.boe.es/diario_boe/xml.php?id=BOE-A-1",
            urlPdf = "https://www.boe.es/boe/dias/2026/10/09/pdfs/BOE-A-1.pdf",
        )

        val conObjeto = clientCon(engineDeTexto(SUMARIO_ITEM_OBJETO)).let {
            BoeSummaryHttpClient(it).fetchSummary(date)
        }
        val conArray = clientCon(engineDeTexto(SUMARIO_ITEM_ARRAY)).let {
            BoeSummaryHttpClient(it).fetchSummary(date)
        }

        assertEquals(listOf(entry), conObjeto)
        assertEquals(conObjeto, conArray)
    }

    @Test
    fun `un 404 devuelve lista vacia`() = runTest {
        val client = clientCon(MockEngine { respondError(HttpStatusCode.NotFound, "<error>No hay BOE</error>") })

        val entries = BoeSummaryHttpClient(client).fetchSummary(date)

        assertTrue(entries.isEmpty())
    }

    @Test
    fun `un estado distinto de 200 y 404 lanza BoeSummaryException`() = runTest {
        val client = clientCon(MockEngine { respondError(HttpStatusCode.InternalServerError, "boom") })

        val error = assertFailsWith<BoeSummaryException> {
            BoeSummaryHttpClient(client).fetchSummary(date)
        }

        assertTrue(error.message!!.contains("500"), error.message!!)
        assertTrue(error.message!!.contains("2026-10-09"), error.message!!)
    }

    @Test
    fun `un fallo de red lanza BoeSummaryException`() = runTest {
        val client = clientCon(MockEngine { throw IOException("sin conexión") })

        val error = assertFailsWith<BoeSummaryException> {
            BoeSummaryHttpClient(client).fetchSummary(date)
        }

        assertTrue(error.message!!.contains("2026-10-09"), error.message!!)
        assertTrue(error.cause is IOException)
    }

    private fun clientCon(engine: MockEngine): HttpClient = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(DatoPublicoJson) }
    }

    private fun engineDeTexto(text: String): MockEngine = MockEngine {
        respond(
            content = text,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/boe/$name")) { "No se encontró /boe/$name" }
            .readBytes().decodeToString()

    private companion object {
        const val SUMARIO_ITEM_OBJETO = """
        {"status":{"code":"200","text":"ok"},
         "data":{"sumario":{"metadatos":{"publicacion":"BOE","fecha_publicacion":"20261009"},
          "diario":{"numero":"1",
           "seccion":{"codigo":"1","nombre":"I.",
            "departamento":{"codigo":"1","nombre":"MINISTERIO X",
             "epigrafe":{"nombre":"Epi",
              "item":{"identificador":"BOE-A-1","control":"2026/1","titulo":"T",
               "url_html":"https://www.boe.es/diario_boe/txt.php?id=BOE-A-1",
               "url_xml":"https://www.boe.es/diario_boe/xml.php?id=BOE-A-1",
               "url_pdf":{"texto":"https://www.boe.es/boe/dias/2026/10/09/pdfs/BOE-A-1.pdf"}}}}}}}}}
        """

        const val SUMARIO_ITEM_ARRAY = """
        {"status":{"code":"200","text":"ok"},
         "data":{"sumario":{"metadatos":{"publicacion":"BOE","fecha_publicacion":"20261009"},
          "diario":[{"numero":"1",
           "seccion":[{"codigo":"1","nombre":"I.",
            "departamento":[{"codigo":"1","nombre":"MINISTERIO X",
             "epigrafe":[{"nombre":"Epi",
              "item":[{"identificador":"BOE-A-1","control":"2026/1","titulo":"T",
               "url_html":"https://www.boe.es/diario_boe/txt.php?id=BOE-A-1",
               "url_xml":"https://www.boe.es/diario_boe/xml.php?id=BOE-A-1",
               "url_pdf":{"texto":"https://www.boe.es/boe/dias/2026/10/09/pdfs/BOE-A-1.pdf"}}]}]}]}]}]}}}
        """
    }
}
