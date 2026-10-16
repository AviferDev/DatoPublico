package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Resumen ciudadano estructurado de una [PublicationDto].
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
data class SummaryDto(
    @SerialName("queCambia")
    val whatChanges: String,
    @SerialName("aQuienAfecta")
    val whoIsAffected: String,
    @SerialName("cifrasClave")
    val keyFigures: List<String> = emptyList(),
    @SerialName("fuenteOficial")
    val officialSource: String,
    @SerialName("avisoIA")
    val aiNotice: Boolean = true,
    @SerialName("plazo")
    val deadline: DeadlineDto? = null,
)
