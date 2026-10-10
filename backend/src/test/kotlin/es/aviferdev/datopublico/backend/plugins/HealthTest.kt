package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.module
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthTest {

    @Test
    fun `health responde 200 con estado ok en JSON`() = testApplication {
        application { module() }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals("""{"status":"ok"}""", response.bodyAsText())
        assertEquals(HealthDto("ok"), DatoPublicoJson.decodeFromString<HealthDto>(response.bodyAsText()))
    }

    @Test
    fun `una ruta desconocida responde 404`() = testApplication {
        application { module() }

        val response = client.get("/no-existe")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
