package es.aviferdev.datopublico.backend.ingestion.summary

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs de transporte del sumario diario del BOE (API de datos abiertos, JSON).
 *
 * Reproducen las claves del sobre tal y como las publica la fuente
 * (`fecha_publicacion`, `sumario_diario`, `url_html`, `url_xml`…). Son tipos
 * **internos de `:backend`**, no forman parte del contrato de `:shared` y no
 * deben filtrarse al dominio; el cliente los traduce a [SummaryEntry].
 *
 * Todos los campos son opcionales y tolerantes a claves desconocidas (la config
 * JSON usa `ignoreUnknownKeys`) porque la estructura varía entre secciones.
 *
 * Cada propiedad lleva `@SerialName` explícito aunque el nombre Kotlin coincida
 * con la clave JSON (regla MUST de `CONSTRAINTS.md` §«Serialización»).
 */
@Serializable
data class BoeSummaryResponseDto(
    @SerialName("status")
    val status: BoeStatusDto? = null,
    @SerialName("data")
    val data: BoeDataDto? = null,
)

@Serializable
data class BoeStatusDto(
    @SerialName("code")
    val code: String? = null,
    @SerialName("text")
    val text: String? = null,
)

@Serializable
data class BoeDataDto(
    @SerialName("sumario")
    val summary: BoeSummaryDto? = null,
)

@Serializable
data class BoeSummaryDto(
    @SerialName("metadatos")
    val metadata: BoeMetadataDto? = null,
    @SerialName("diario")
    @Serializable(with = DailyListSerializer::class)
    val daily: List<BoeDailyDto> = emptyList(),
)

@Serializable
data class BoeMetadataDto(
    @SerialName("publicacion")
    val publication: String? = null,
    @SerialName("fecha_publicacion")
    val publicationDate: String? = null,
)

@Serializable
data class BoeDailyDto(
    @SerialName("numero")
    val number: String? = null,
    @SerialName("seccion")
    @Serializable(with = SectionListSerializer::class)
    val section: List<BoeSectionNodeDto> = emptyList(),
    @SerialName("sumario_diario")
    val dailySummary: BoeSummaryDailyDto? = null,
)

@Serializable
data class BoeSummaryDailyDto(
    @SerialName("identificador")
    val identifier: String? = null,
    @SerialName("url_pdf")
    val pdfUrl: BoePdfUrlDto? = null,
)

@Serializable
data class BoeSectionNodeDto(
    @SerialName("codigo")
    val code: String? = null,
    @SerialName("nombre")
    val name: String? = null,
    @SerialName("departamento")
    @Serializable(with = DepartmentListSerializer::class)
    val department: List<BoeDepartmentDto> = emptyList(),
)

@Serializable
data class BoeDepartmentDto(
    @SerialName("codigo")
    val code: String? = null,
    @SerialName("nombre")
    val name: String? = null,
    @SerialName("epigrafe")
    @Serializable(with = HeadingListSerializer::class)
    val heading: List<BoeHeadingDto> = emptyList(),
    @SerialName("item")
    @Serializable(with = ItemListSerializer::class)
    val item: List<BoeSummaryItemDto> = emptyList(),
    @SerialName("texto")
    val text: BoeTextDto? = null,
)

/**
 * Envoltorio `departamento.texto` de las Secciones IV (Justicia) y V.C (anuncios
 * particulares): agrupa las entradas bajo `texto.item` en lugar de exponerlas
 * directamente en `departamento.item`.
 */
@Serializable
data class BoeTextDto(
    @SerialName("item")
    @Serializable(with = ItemListSerializer::class)
    val item: List<BoeSummaryItemDto> = emptyList(),
)

@Serializable
data class BoeHeadingDto(
    @SerialName("nombre")
    val name: String? = null,
    @SerialName("item")
    @Serializable(with = ItemListSerializer::class)
    val item: List<BoeSummaryItemDto> = emptyList(),
)

@Serializable
data class BoeSummaryItemDto(
    @SerialName("identificador")
    val identifier: String? = null,
    @SerialName("control")
    val control: String? = null,
    @SerialName("titulo")
    val title: String? = null,
    @SerialName("url_html")
    val htmlUrl: String? = null,
    @SerialName("url_xml")
    val xmlUrl: String? = null,
    @SerialName("url_pdf")
    val pdfUrl: BoePdfUrlDto? = null,
)

@Serializable
data class BoePdfUrlDto(
    @SerialName("szBytes")
    val szBytes: String? = null,
    @SerialName("szKBytes")
    val szKBytes: String? = null,
    @SerialName("pagina_inicial")
    val firstPage: String? = null,
    @SerialName("pagina_final")
    val lastPage: String? = null,
    @SerialName("texto")
    val text: String? = null,
)
