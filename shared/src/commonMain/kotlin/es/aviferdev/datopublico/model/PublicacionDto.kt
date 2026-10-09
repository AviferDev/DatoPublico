package es.aviferdev.datopublico.model

import kotlinx.serialization.Serializable

/**
 * Publicación del BOE: unidad base del contrato compartido entre backend y
 * cliente.
 *
 * [fechaPublicacion] es una fecha ISO-8601 como `String` (sin `java.time`).
 * [epigrafe], [resumen] y [plazo] son opcionales según la fuente. La sección del
 * BOE y su epígrafe se conservan como metadato de trazabilidad.
 */
@Serializable
data class PublicacionDto(
    val id: String,
    val titulo: String,
    val fechaPublicacion: String,
    val organismo: String?,
    val seccion: SeccionBoe,
    val epigrafe: String?,
    val categoria: Categoria,
    val urlOficial: String,
    val resumen: ResumenDto? = null,
    val plazo: PlazoDto? = null,
)
