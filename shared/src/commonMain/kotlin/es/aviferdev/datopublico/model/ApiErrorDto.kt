package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cuerpo de **error tipado** de la API pública (FT00030).
 *
 * Es el contrato de transporte de **cualquier** error de la API
 * (`400`/`404`/`429`/`500`/`502`): en lugar de una respuesta sin cuerpo, el
 * servidor devuelve `{"codigo":"…","mensaje":"…"}`. Vive en `:shared` porque es
 * un contrato **público** que consumirá el cliente (CMP) y que el documento
 * OpenAPI documenta; el `HealthDto`, al ser operacional, sigue en `:backend`.
 *
 * El `mensaje` es **genérico** y estable (en castellano): **nunca** filtra
 * detalles internos (trazas, SQL, mensajes de proveedores). Las propiedades
 * llevan `@SerialName` explícito (regla MUST de `CONSTRAINTS.md`
 * §«Serialización»).
 *
 * @property code código de error de wire ([ApiErrorCodeDto]).
 * @property message mensaje genérico dirigido al cliente.
 */
@Serializable
data class ApiErrorDto(
    @SerialName("codigo")
    val code: ApiErrorCodeDto,
    @SerialName("mensaje")
    val message: String,
)

/**
 * Código de error de **wire** (token `snake_case`) del contrato [ApiErrorDto].
 *
 * Cada valor se corresponde con un estado HTTP estable: `solicitud_invalida`
 * (`400`), `no_encontrado` (`404`), `demasiadas_peticiones` (`429`),
 * `error_interno` (`500`) y `servicio_no_disponible` (`502`). Son tokens
 * **estables y localizables** (como `ChatStatusDto`/`CategoriaDto`): **no** deben
 * renombrarse sin una migración de contrato.
 */
@Serializable
enum class ApiErrorCodeDto {
    /** La petición no es válida (parámetros, cuerpo o formato). */
    @SerialName("solicitud_invalida")
    INVALID_REQUEST,

    /** El recurso solicitado no existe o no está disponible. */
    @SerialName("no_encontrado")
    NOT_FOUND,

    /** Se ha superado el límite de peticiones por IP. */
    @SerialName("demasiadas_peticiones")
    TOO_MANY_REQUESTS,

    /** Fallo interno no controlado del servidor. */
    @SerialName("error_interno")
    INTERNAL_ERROR,

    /** El proveedor de generación de texto no está disponible. */
    @SerialName("servicio_no_disponible")
    SERVICE_UNAVAILABLE,
}
