package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.serialization.DatoPublicoJson
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tests de gate de la ruta `GET /openapi.json` (FT00029) con `testApplication`
 * (sin red, BD, modelo ni OpenCode): sirve `200` `application/json` con un
 * documento OpenAPI 3.1 válido y parseable, declara `info.version` y describe los
 * **cuatro** endpoints de dominio bajo `/v1` más `/health`; sin dependencias,
 * `/openapi.json` y `/health` siguen operativos y las rutas de dominio responden
 * `404`.
 */
class OpenApiRouteTest {

    @Test
    fun `openapi json is served as valid openapi 3_1`() = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }

        val response = client.get("/openapi.json")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val document = documentOf(response.bodyAsText())
        assertEquals("3.1.0", document.getValue("openapi").jsonPrimitive.content)
        val info = document.getValue("info").jsonObject
        assertEquals("DatoPublico API", info.getValue("title").jsonPrimitive.content)
        assertEquals("0.5.0", info.getValue("version").jsonPrimitive.content)
    }

    @Test
    fun `openapi json documents the domain endpoints and health`() = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }

        val paths = documentOf(client.get("/openapi.json").bodyAsText()).getValue("paths").jsonObject

        assertTrue(paths.containsKey("/v1/feed"))
        assertTrue(paths.containsKey("/v1/publicacion/{id}"))
        assertTrue(paths.containsKey("/v1/buscar"))
        assertTrue(paths.containsKey("/v1/chat"))
        assertTrue(paths.containsKey("/health"))
    }

    @Test
    fun `openapi and health are served without dependencies and domain routes are 404`() = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }

        assertEquals(HttpStatusCode.OK, client.get("/openapi.json").status)
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/feed?fecha=2026-10-11").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/buscar?q=tema").status)
        assertEquals(HttpStatusCode.NotFound, client.post("/v1/chat").status)
    }

    /** Parsea [body] como objeto JSON (falla si el documento no es JSON válido). */
    private fun documentOf(body: String): JsonObject =
        DatoPublicoJson.parseToJsonElement(body).jsonObject
}
