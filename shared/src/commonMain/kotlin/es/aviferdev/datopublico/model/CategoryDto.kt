package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Familia estable de cara al ciudadano para clasificar una [PublicationDto] del BOE.
 *
 * La taxonomía es un contrato curado de 11 valores. Cada valor declara un
 * `@SerialName` en `snake_case`, **sin acentos ni espacios**: es el token de wire
 * compartido entre backend y cliente y no debe renombrarse sin una migración.
 */
@Serializable
enum class CategoryDto {
    @SerialName("normas_y_legislacion")
    NORMS_AND_LEGISLATION,

    @SerialName("nombramientos_y_ceses")
    APPOINTMENTS_AND_DISMISSALS,

    @SerialName("oposiciones_y_empleo_publico")
    PUBLIC_EMPLOYMENT_AND_JOB_OFFERS,

    @SerialName("becas_subvenciones_y_ayudas")
    SCHOLARSHIPS_GRANTS_AND_AID,

    @SerialName("premios")
    AWARDS,

    @SerialName("convenios_y_acuerdos")
    AGREEMENTS_AND_CONVENTIONS,

    @SerialName("educacion_y_planes_de_estudio")
    EDUCATION_AND_STUDY_PLANS,

    @SerialName("medio_ambiente")
    ENVIRONMENT,

    @SerialName("recursos_y_resoluciones")
    APPEALS_AND_RESOLUTIONS,

    @SerialName("informacion_publica_y_concesiones")
    PUBLIC_INFORMATION_AND_CONCESSIONS,

    @SerialName("otras_disposiciones_y_anuncios")
    OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
}
