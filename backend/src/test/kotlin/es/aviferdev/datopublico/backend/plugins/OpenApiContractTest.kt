package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.model.ApiErrorCodeDto
import es.aviferdev.datopublico.model.ApiErrorDto
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.ChatCitationDto
import es.aviferdev.datopublico.model.ChatRequestDto
import es.aviferdev.datopublico.model.ChatResponseDto
import es.aviferdev.datopublico.model.ChatStatusDto
import es.aviferdev.datopublico.model.HighlightDto
import es.aviferdev.datopublico.model.HighlightReasonDto
import es.aviferdev.datopublico.model.PlazoDto
import es.aviferdev.datopublico.model.PublicacionDto
import es.aviferdev.datopublico.model.ResumenDiarioDto
import es.aviferdev.datopublico.model.ResumenDto
import es.aviferdev.datopublico.model.SearchResultsDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Test **anti-deriva** del contrato OpenAPI (FT00029).
 *
 * El documento es estático; este gate lo mantiene alineado con los DTOs de wire
 * de `:shared` (y el `HealthDto` operacional de `:backend`): para cada esquema de
 * objeto, el conjunto de claves documentadas debe coincidir con los `@SerialName`
 * de su `serializer().descriptor.elementNames`, y para cada enum de wire, los
 * valores documentados deben coincidir con sus tokens. Una divergencia (un DTO
 * que cambia sin actualizar el documento) **falla** el test.
 */
class OpenApiContractTest {

    @Test
    fun `documented schemas match the serial names of the shared dtos`() =
        withDocument { document ->
            val mismatches = SCHEMA_SERIALIZERS.filter { (name, serializer) ->
                documentedProperties(document, name) != serialNames(serializer)
            }.keys
            assertTrue(mismatches.isEmpty(), "Esquemas desalineados con los @SerialName: $mismatches")
        }

    @Test
    fun `documented wire enums match the shared tokens`() =
        withDocument { document ->
            val mismatches = ENUM_SERIALIZERS.filter { (name, serializer) ->
                documentedEnumValues(document, name) != serialNames(serializer)
            }.keys
            assertTrue(mismatches.isEmpty(), "Enums desalineados con los tokens de wire: $mismatches")
        }

    /** Arranca la aplicación sin dependencias, obtiene el documento y lo inspecciona. */
    private fun withDocument(assertions: (JsonObject) -> Unit) = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }
        val document = DatoPublicoJson.parseToJsonElement(client.get("/openapi.json").bodyAsText()).jsonObject
        assertions(document)
    }

    /** Nombres de propiedad documentados del esquema [name]. */
    private fun documentedProperties(document: JsonObject, name: String): Set<String> =
        schemaOf(document, name).getValue("properties").jsonObject.keys

    /** Valores de enum documentados del esquema [name]. */
    private fun documentedEnumValues(document: JsonObject, name: String): Set<String> =
        schemaOf(document, name).getValue("enum").jsonArray
            .map { element -> element.jsonPrimitive.content }
            .toSet()

    /** Esquema [name] de `components.schemas` (falla si no está documentado). */
    private fun schemaOf(document: JsonObject, name: String): JsonObject =
        document.getValue("components").jsonObject
            .getValue("schemas").jsonObject
            .getValue(name).jsonObject

    /** Nombres de propiedad de [serializer]: sus `@SerialName` (o tokens, si es enum). */
    private fun serialNames(serializer: KSerializer<*>): Set<String> =
        serializer.descriptor.elementNames.toSet()
}

/** Esquemas de objeto del documento y el DTO de `:shared`/`:backend` que espejan. */
private val SCHEMA_SERIALIZERS: Map<String, KSerializer<*>> = mapOf(
    "PublicacionDto" to PublicacionDto.serializer(),
    "ResumenDto" to ResumenDto.serializer(),
    "PlazoDto" to PlazoDto.serializer(),
    "ResumenDiarioDto" to ResumenDiarioDto.serializer(),
    "HighlightDto" to HighlightDto.serializer(),
    "SearchResultsDto" to SearchResultsDto.serializer(),
    "ChatRequestDto" to ChatRequestDto.serializer(),
    "ChatResponseDto" to ChatResponseDto.serializer(),
    "ChatCitationDto" to ChatCitationDto.serializer(),
    "HealthDto" to HealthDto.serializer(),
    "ApiErrorDto" to ApiErrorDto.serializer(),
)

/** Enums de wire del documento y el enum de `:shared` cuyos tokens espejan. */
private val ENUM_SERIALIZERS: Map<String, KSerializer<*>> = mapOf(
    "CategoriaDto" to CategoriaDto.serializer(),
    "SeccionBoeDto" to SeccionBoeDto.serializer(),
    "HighlightReasonDto" to HighlightReasonDto.serializer(),
    "ChatStatusDto" to ChatStatusDto.serializer(),
    "ApiErrorCodeDto" to ApiErrorCodeDto.serializer(),
)
