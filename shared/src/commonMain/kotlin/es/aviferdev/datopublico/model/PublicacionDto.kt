package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Publicación del BOE: unidad base del contrato compartido entre backend y
 * cliente.
 *
 * [fechaPublicacion] es una fecha ISO-8601 como `String` (sin `java.time`).
 * [epigrafe], [resumen] y [plazo] son opcionales según la fuente. La sección del
 * BOE y su epígrafe se conservan como metadato de trazabilidad.
 *
 * Cada propiedad lleva `@SerialName` explícito aunque el nombre Kotlin coincida
 * con la clave JSON (regla MUST de `CONSTRAINTS.md` §«Serialización»).
 */
@Serializable
data class PublicacionDto(
    @SerialName("id")
    val id: String,
    @SerialName("titulo")
    val titulo: String,
    @SerialName("fechaPublicacion")
    val fechaPublicacion: String,
    @SerialName("organismo")
    val organismo: String?,
    @SerialName("seccion")
    val seccion: SeccionBoeDto,
    @SerialName("epigrafe")
    val epigrafe: String?,
    @SerialName("categoria")
    val categoria: CategoriaDto,
    @SerialName("urlOficial")
    val urlOficial: String,
    @SerialName("resumen")
    val resumen: ResumenDto? = null,
    @SerialName("plazo")
    val plazo: PlazoDto? = null,
)
