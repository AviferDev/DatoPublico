package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Familia estable de cara al ciudadano para clasificar una [PublicacionDto] del BOE.
 *
 * La taxonomía es un contrato curado de 11 valores. Cada valor declara un
 * `@SerialName` en `snake_case`, **sin acentos ni espacios**: es el token de wire
 * compartido entre backend y cliente y no debe renombrarse sin una migración.
 */
@Serializable
enum class Categoria {
    @SerialName("normas_y_legislacion")
    NORMAS_Y_LEGISLACION,

    @SerialName("nombramientos_y_ceses")
    NOMBRAMIENTOS_Y_CESES,

    @SerialName("oposiciones_y_empleo_publico")
    OPOSICIONES_Y_EMPLEO_PUBLICO,

    @SerialName("becas_subvenciones_y_ayudas")
    BECAS_SUBVENCIONES_Y_AYUDAS,

    @SerialName("premios")
    PREMIOS,

    @SerialName("convenios_y_acuerdos")
    CONVENIOS_Y_ACUERDOS,

    @SerialName("educacion_y_planes_de_estudio")
    EDUCACION_Y_PLANES_DE_ESTUDIO,

    @SerialName("medio_ambiente")
    MEDIO_AMBIENTE,

    @SerialName("recursos_y_resoluciones")
    RECURSOS_Y_RESOLUCIONES,

    @SerialName("informacion_publica_y_concesiones")
    INFORMACION_PUBLICA_Y_CONCESIONES,

    @SerialName("otras_disposiciones_y_anuncios")
    OTRAS_DISPOSICIONES_Y_ANUNCIOS,
}
