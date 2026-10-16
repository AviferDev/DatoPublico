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
data class PublicationDto(
    @SerialName("id")
    val id: String,
    @SerialName("titulo")
    val title: String,
    @SerialName("fechaPublicacion")
    val publicationDate: String,
    @SerialName("organismo")
    val issuingBody: String?,
    @SerialName("seccion")
    val section: SeccionBoeDto,
    @SerialName("epigrafe")
    val heading: String?,
    @SerialName("categoria")
    val category: CategoriaDto,
    @SerialName("urlOficial")
    val officialUrl: String,
    @SerialName("resumen")
    val summary: SummaryDto? = null,
    @SerialName("plazo")
    val deadline: DeadlineDto? = null,
)
