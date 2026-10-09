package es.aviferdev.datopublico.model

import kotlinx.serialization.Serializable

/**
 * Resumen ciudadano estructurado de una [PublicacionDto].
 *
 * Modela las secciones «¿Qué cambia?», «¿A quién afecta?» y «Cifras clave», más la
 * fuente oficial y el aviso de IA. La obligatoriedad y validación de
 * [fuenteOficial] corresponden a una feature posterior (FT00019): aquí solo se
 * modela.
 */
@Serializable
data class ResumenDto(
    val queCambia: String,
    val aQuienAfecta: String,
    val cifrasClave: List<String> = emptyList(),
    val fuenteOficial: String,
    val avisoIA: Boolean = true,
    val plazo: PlazoDto? = null,
)
