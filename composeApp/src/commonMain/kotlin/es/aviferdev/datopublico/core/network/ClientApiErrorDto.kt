package es.aviferdev.datopublico.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO de **error de wire** del cliente (FT00035).
 *
 * Espeja la forma de `ApiErrorDto` de `:shared` (`{"codigo":…,"mensaje":…}`) como
 * **capa anti-corrupción** propia del cliente, sin depender de `:shared` (que no
 * es multiplataforma). El `codigo` viaja como el token `snake_case` **en crudo**
 * (String): así el cliente tolera códigos nuevos del servidor sin romper la
 * decodificación ni obligar a duplicar el enum `ApiErrorCodeDto`.
 *
 * Los campos son opcionales para que un cuerpo parcial siga decodificando; si el
 * cuerpo no es JSON válido, el cliente cae a [ApiFailure.Server] con `code`
 * nulo, sin propagar excepción.
 *
 * Cada propiedad lleva `@SerialName` explícito aunque el nombre Kotlin coincida
 * con la clave JSON (regla MUST de `CONSTRAINTS.md` §«Serialización»).
 *
 * @property code token de error (`solicitud_invalida`, `no_encontrado`,
 *   `demasiadas_peticiones`, `error_interno`, `servicio_no_disponible`).
 * @property message mensaje genérico dirigido al cliente.
 */
@Serializable
data class ClientApiErrorDto(
    @SerialName("codigo")
    val code: String? = null,
    @SerialName("mensaje")
    val message: String? = null,
)
