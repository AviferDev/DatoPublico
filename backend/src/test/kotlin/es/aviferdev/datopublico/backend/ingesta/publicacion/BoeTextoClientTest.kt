package es.aviferdev.datopublico.backend.ingesta.publicacion

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Cliente del texto del BOE con [MockEngine] (sin red): cubre los escenarios 5 y
 * 6 del spec — petición correcta, `404` → `null`, error tipado y cuerpo ilegible.
 */
class BoeTextoClientTest {

    @Test
    fun `pide GET a la url absoluta con Accept application xml y parsea el documento`() = runTest {
        var capturada: HttpRequestData? = null
        val client = clientCon(
            MockEngine { request ->
                capturada = request
                respond(
                    content = recurso("texto-IV-2024-1.xml"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/xml"),
                )
            },
        )

        val documento = assertNotNull(BoeTextoHttpClient(client).obtenerDocumento(URL_XML))
        val request = assertNotNull(capturada)

        assertEquals(HttpMethod.Get, request.method)
        assertEquals(URL_XML, request.url.toString())
        assertEquals("application/xml", request.headers[HttpHeaders.Accept])
        assertEquals("JUZGADOS DE PRIMERA INSTANCIA E INSTRUCCIÓN", documento.metadatos.departamento)
        assertNotNull(documento.texto)
    }

    @Test
    fun `decodifica el cuerpo como UTF-8 conservando acentos`() = runTest {
        val client = clientCon(
            MockEngine {
                respond(
                    content = recurso("texto-IV-2024-1.xml"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/xml"),
                )
            },
        )

        val documento = assertNotNull(BoeTextoHttpClient(client).obtenerDocumento(URL_XML))

        assertTrue(assertNotNull(documento.texto).contains("vía de apremio"), documento.texto!!)
    }

    @Test
    fun `un 404 devuelve null`() = runTest {
        val client = clientCon(MockEngine { respondError(HttpStatusCode.NotFound, "sin texto") })

        assertNull(BoeTextoHttpClient(client).obtenerDocumento(URL_XML))
    }

    @Test
    fun `un estado distinto de 200 y 404 lanza BoeTextoException`() = runTest {
        val client = clientCon(MockEngine { respondError(HttpStatusCode.InternalServerError, "boom") })

        val error = assertFailsWith<BoeTextoException> {
            BoeTextoHttpClient(client).obtenerDocumento(URL_XML)
        }

        assertTrue(error.message!!.contains("500"), error.message!!)
        assertTrue(error.message!!.contains("BOE-A-1"), error.message!!)
    }

    @Test
    fun `un fallo de red lanza BoeTextoException`() = runTest {
        val client = clientCon(MockEngine { throw IOException("sin conexión") })

        val error = assertFailsWith<BoeTextoException> {
            BoeTextoHttpClient(client).obtenerDocumento(URL_XML)
        }

        assertTrue(error.message!!.contains("BOE-A-1"), error.message!!)
        assertTrue(error.cause is IOException)
    }

    @Test
    fun `un cuerpo xml malformado lanza BoeTextoException`() = runTest {
        val client = clientCon(
            MockEngine {
                respond(
                    content = "<documento><texto><p>sin cerrar",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/xml"),
                )
            },
        )

        val error = assertFailsWith<BoeTextoException> {
            BoeTextoHttpClient(client).obtenerDocumento(URL_XML)
        }

        assertTrue(error.message!!.contains("ilegible"), error.message!!)
    }

    private fun clientCon(engine: MockEngine): HttpClient = HttpClient(engine) { expectSuccess = false }

    /** Lee un recurso de `src/test/resources/boe` como texto UTF-8. */
    private fun recurso(ruta: String): String =
        checkNotNull(javaClass.getResourceAsStream("/boe/$ruta")) { "No se encontró /boe/$ruta" }
            .readBytes()
            .decodeToString()

    private companion object {
        const val URL_XML = "https://www.boe.es/diario_boe/xml.php?id=BOE-A-1"
    }
}
