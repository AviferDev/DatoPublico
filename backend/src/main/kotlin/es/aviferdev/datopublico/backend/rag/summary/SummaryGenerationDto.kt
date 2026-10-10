package es.aviferdev.datopublico.backend.rag.summary

import es.aviferdev.datopublico.model.PlazoDto
import es.aviferdev.datopublico.model.ResumenDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Salida **estructurada** del modelo para el resumen ciudadano (DTO de transporte,
 * sufijo `Dto`).
 *
 * Es lo que el modelo debe devolver como JSON y lo que parsea
 * [SummaryOutputParser]; **no** incluye la fuente oficial ni el aviso de IA porque
 * no los produce el modelo: los inyecta de forma determinista [toResumenDto] a
 * partir de la publicación de origen.
 *
 * Cada propiedad lleva `@SerialName` explícito (regla MUST de `CONSTRAINTS.md`
 * §«Serialización»).
 *
 * @property queCambia sección «¿Qué cambia?».
 * @property aQuienAfecta sección «¿A quién afecta?».
 * @property cifrasClave cifras o datos concretos; puede estar vacía.
 * @property plazo plazo de solicitud; solo en convocatorias de empleo o becas.
 */
@Serializable
data class SummaryGenerationDto(
    @SerialName("queCambia") val queCambia: String,
    @SerialName("aQuienAfecta") val aQuienAfecta: String,
    @SerialName("cifrasClave") val cifrasClave: List<String> = emptyList(),
    @SerialName("plazo") val plazo: PlazoDto? = null,
)

/**
 * Convierte la salida del modelo en el contrato de wire [ResumenDto],
 * **inyectando** la fuente oficial y el aviso de IA.
 *
 * La fuente **no** la produce el modelo (así la cita a la fuente oficial siempre
 * está presente y no depende de la IA); se toma del `urlOficial` de la
 * publicación. [avisoIA] se fija a `true` (el aviso visible se exige en FT00023).
 *
 * @param source URL oficial de la publicación de origen.
 * @return el resumen listo para validar con `CitizenSummaryValidator`.
 */
fun SummaryGenerationDto.toResumenDto(source: String): ResumenDto = ResumenDto(
    queCambia = queCambia,
    aQuienAfecta = aQuienAfecta,
    cifrasClave = cifrasClave,
    fuenteOficial = source,
    avisoIA = true,
    plazo = plazo,
)
