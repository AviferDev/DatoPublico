package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Resumen ciudadano estructurado de una [PublicacionDto].
 *
 * Modela las secciones «¿Qué cambia?», «¿A quién afecta?» y «Cifras clave», más la
 * fuente oficial y el aviso de IA. La obligatoriedad y validación de
 * [fuenteOficial] corresponden a una feature posterior (FT00019): aquí solo se
 * modela.
 *
 * Cada propiedad lleva `@SerialName` explícito aunque el nombre Kotlin coincida
 * con la clave JSON (regla MUST de `CONSTRAINTS.md` §«Serialización»).
 */
@Serializable
data class ResumenDto(
    @SerialName("queCambia")
    val queCambia: String,
    @SerialName("aQuienAfecta")
    val aQuienAfecta: String,
    @SerialName("cifrasClave")
    val cifrasClave: List<String> = emptyList(),
    @SerialName("fuenteOficial")
    val fuenteOficial: String,
    @SerialName("avisoIA")
    val avisoIA: Boolean = true,
    @SerialName("plazo")
    val plazo: PlazoDto? = null,
)
