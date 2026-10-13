package es.aviferdev.datopublico.model

import es.aviferdev.datopublico.serialization.DatoPublicoJson
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests de gate del **contrato de error tipado** (FT00030) en `:shared`: los
 * **tokens `@SerialName`** estables del código (`snake_case`) y el *round-trip*
 * del cuerpo `{"codigo":…,"mensaje":…}`. No usan red ni base de datos.
 */
class ErrorContractTest {

    @Test
    fun `api error code tokens are stable snake case`() {
        val tokens = ApiErrorCodeDto.entries.associateWith { code ->
            DatoPublicoJson.encodeToString(ApiErrorCodeDto.serializer(), code)
        }

        assertEquals("\"solicitud_invalida\"", tokens[ApiErrorCodeDto.INVALID_REQUEST])
        assertEquals("\"no_encontrado\"", tokens[ApiErrorCodeDto.NOT_FOUND])
        assertEquals("\"demasiadas_peticiones\"", tokens[ApiErrorCodeDto.TOO_MANY_REQUESTS])
        assertEquals("\"error_interno\"", tokens[ApiErrorCodeDto.INTERNAL_ERROR])
        assertEquals("\"servicio_no_disponible\"", tokens[ApiErrorCodeDto.SERVICE_UNAVAILABLE])
    }

    @Test
    fun `api error round trips with the codigo and mensaje keys`() {
        val error = ApiErrorDto(
            code = ApiErrorCodeDto.TOO_MANY_REQUESTS,
            message = "Se ha superado el límite de peticiones.",
        )

        val json = DatoPublicoJson.encodeToString(ApiErrorDto.serializer(), error)
        val decoded = DatoPublicoJson.decodeFromString(ApiErrorDto.serializer(), json)

        assertEquals(error, decoded)
        assertEquals(
            """{"codigo":"demasiadas_peticiones","mensaje":"Se ha superado el límite de peticiones."}""",
            json,
        )
    }
}
