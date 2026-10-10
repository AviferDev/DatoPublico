package es.aviferdev.datopublico.backend.ingesta.sumario

import es.aviferdev.datopublico.model.SeccionBoeDto
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
 * spec — URL y cabecera, filtrado de secciones, `item` objeto/array, `404` y
 * errores tipados.
 */
class BoeSumarioClientTest {

    private val fecha = LocalDate.of(2026, 10, 9)

    @Test
    fun `pide GET YYYYMMDD con Accept application json y filtra las secciones`() = runTest {
        var capturada: HttpRequestData? = null
        val client = clientCon(MockEngine { request ->
            capturada = request
            respond(
                content = fixture("sumario-20261009.json"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })

        val entradas = BoeSumarioHttpClient(client, baseUrl = "https://www.boe.es/datosabiertos/api")
            .obtenerSumario(fecha)

        val request = checkNotNull(capturada)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals(
            "https://www.boe.es/datosabiertos/api/boe/sumario/20261009",
            request.url.toString(),
        )
        assertEquals("application/json", request.headers[HttpHeaders.Accept])
        assertEquals(205, entradas.size)
        assertEquals(
            setOf(SeccionBoeDto.I, SeccionBoeDto.II_A, SeccionBoeDto.II_B, SeccionBoeDto.III, SeccionBoeDto.V_B),
            entradas.map { it.seccion }.toSet(),
        )
        assertTrue(entradas.none { it.identificador == "BOE-B-2026-32742" }) // Sección V.A (32 en la fuente)
    }

    @Test
    fun `item como objeto o como array produce las mismas entradas`() = runTest {
        val entrada = EntradaSumario(
            identificador = "BOE-A-1",
            control = "2026/1",
            titulo = "T",
            fechaPublicacion = "2026-10-09",
            seccion = SeccionBoeDto.I,
            organismo = "MINISTERIO X",
            epigrafe = "Epi",
            urlOficial = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-1",
            urlXml = "https://www.boe.es/diario_boe/xml.php?id=BOE-A-1",
        )

        val conObjeto = clientCon(engineDeTexto(SUMARIO_ITEM_OBJETO)).let {
            BoeSumarioHttpClient(it).obtenerSumario(fecha)
        }
        val conArray = clientCon(engineDeTexto(SUMARIO_ITEM_ARRAY)).let {
            BoeSumarioHttpClient(it).obtenerSumario(fecha)
        }

        assertEquals(listOf(entrada), conObjeto)
        assertEquals(conObjeto, conArray)
    }

    @Test
    fun `un 404 devuelve lista vacia`() = runTest {
        val client = clientCon(MockEngine { respondError(HttpStatusCode.NotFound, "<error>No hay BOE</error>") })

        val entradas = BoeSumarioHttpClient(client).obtenerSumario(fecha)

        assertTrue(entradas.isEmpty())
    }

    @Test
    fun `un estado distinto de 200 y 404 lanza BoeSumarioException`() = runTest {
        val client = clientCon(MockEngine { respondError(HttpStatusCode.InternalServerError, "boom") })

        val error = assertFailsWith<BoeSumarioException> {
            BoeSumarioHttpClient(client).obtenerSumario(fecha)
        }

        assertTrue(error.message!!.contains("500"), error.message!!)
        assertTrue(error.message!!.contains("2026-10-09"), error.message!!)
    }

    @Test
    fun `un fallo de red lanza BoeSumarioException`() = runTest {
        val client = clientCon(MockEngine { throw IOException("sin conexión") })

        val error = assertFailsWith<BoeSumarioException> {
            BoeSumarioHttpClient(client).obtenerSumario(fecha)
        }

        assertTrue(error.message!!.contains("2026-10-09"), error.message!!)
        assertTrue(error.cause is IOException)
    }

    private fun clientCon(engine: MockEngine): HttpClient = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(DatoPublicoJson) }
    }

    private fun engineDeTexto(texto: String): MockEngine = MockEngine {
        respond(
            content = texto,
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
               "url_xml":"https://www.boe.es/diario_boe/xml.php?id=BOE-A-1"}}}}}}}}
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
               "url_xml":"https://www.boe.es/diario_boe/xml.php?id=BOE-A-1"}]}]}]}]}]}}}
        """
    }
}
