package es.aviferdev.datopublico.backend.plugins

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `GET /openapi.json` (FT00029): sirve el **contrato OpenAPI 3.1** del backend.
 *
 * El documento es un artefacto **estático y versionado** que vive en los recursos
 * del módulo (`openapi/openapi.json`): no se genera desde las rutas ni desde los
 * DTOs (el compilador OpenAPI de Ktor exige Kotlin ≥ 2.2.20 y el proyecto fija
 * 2.1.20) y no añade ninguna dependencia Gradle. Se carga **una sola vez** desde
 * el classpath y se sirve como `application/json`; si el recurso no está
 * empaquetado, el arranque **falla en claro** (fail-fast) porque el contrato no
 * existiría en silencio.
 *
 * La ruta no depende de la base de datos, del modelo ni de OpenCode: se registra
 * **siempre**, es meta/operacional y va **sin** versión (igual que `/health`). Un
 * test anti-deriva (`OpenApiContractTest`) mantiene los esquemas documentados
 * alineados con los `@SerialName` y los tokens de los DTOs de `:shared`.
 */
fun Route.openApiRoutes() {
    get("/openapi.json") {
        call.respondText(OpenApiDocument.content, ContentType.Application.Json, HttpStatusCode.OK)
    }
}

/** Ruta del recurso versionado que contiene el documento OpenAPI 3.1. */
private const val OPENAPI_RESOURCE = "openapi/openapi.json"

/**
 * Documento OpenAPI 3.1 **versionado**, cargado de forma perezosa y **única**
 * desde los recursos del módulo.
 *
 * La carga es *fail-fast*: un recurso ausente es un error de empaquetado, no un
 * estado esperado, así que lanza `IllegalStateException` con un mensaje claro en
 * lugar de servir un contrato vacío.
 */
private object OpenApiDocument {

    /** Contenido del documento OpenAPI servido por `GET /openapi.json`. */
    val content: String by lazy { read() }

    /** Lee [OPENAPI_RESOURCE] del classpath; falla si no está empaquetado. */
    private fun read(): String =
        OpenApiDocument::class.java.classLoader.getResourceAsStream(OPENAPI_RESOURCE)?.use { stream ->
            stream.readBytes().decodeToString()
        } ?: error("No se encontró el recurso OpenAPI: '$OPENAPI_RESOURCE'.")
}
