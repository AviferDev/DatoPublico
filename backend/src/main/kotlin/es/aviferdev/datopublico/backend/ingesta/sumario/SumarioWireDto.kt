package es.aviferdev.datopublico.backend.ingesta.sumario

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs de transporte del sumario diario del BOE (API de datos abiertos, JSON).
 *
 * Reproducen las claves del sobre tal y como las publica la fuente
 * (`fecha_publicacion`, `sumario_diario`, `url_html`, `url_xml`…). Son tipos
 * **internos de `:backend`**, no forman parte del contrato de `:shared` y no
 * deben filtrarse al dominio; el cliente los traduce a [EntradaSumario].
 *
 * Todos los campos son opcionales y tolerantes a claves desconocidas (la config
 * JSON usa `ignoreUnknownKeys`) porque la estructura varía entre secciones.
 *
 * Cada propiedad lleva `@SerialName` explícito aunque el nombre Kotlin coincida
 * con la clave JSON (regla MUST de `CONSTRAINTS.md` §«Serialización»).
 */
@Serializable
data class BoeSumarioResponseDto(
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
    val sumario: BoeSumarioDto? = null,
)

@Serializable
data class BoeSumarioDto(
    @SerialName("metadatos")
    val metadatos: BoeMetadatosDto? = null,
    @SerialName("diario")
    @Serializable(with = DiarioListSerializer::class)
    val diario: List<BoeDiarioDto> = emptyList(),
)

@Serializable
data class BoeMetadatosDto(
    @SerialName("publicacion")
    val publicacion: String? = null,
    @SerialName("fecha_publicacion")
    val fechaPublicacion: String? = null,
)

@Serializable
data class BoeDiarioDto(
    @SerialName("numero")
    val numero: String? = null,
    @SerialName("seccion")
    @Serializable(with = SeccionListSerializer::class)
    val seccion: List<BoeSeccionDto> = emptyList(),
    @SerialName("sumario_diario")
    val sumarioDiario: BoeSumarioDiarioDto? = null,
)

@Serializable
data class BoeSumarioDiarioDto(
    @SerialName("identificador")
    val identificador: String? = null,
    @SerialName("url_pdf")
    val urlPdf: BoeUrlPdfDto? = null,
)

@Serializable
data class BoeSeccionDto(
    @SerialName("codigo")
    val codigo: String? = null,
    @SerialName("nombre")
    val nombre: String? = null,
    @SerialName("departamento")
    @Serializable(with = DepartamentoListSerializer::class)
    val departamento: List<BoeDepartamentoDto> = emptyList(),
)

@Serializable
data class BoeDepartamentoDto(
    @SerialName("codigo")
    val codigo: String? = null,
    @SerialName("nombre")
    val nombre: String? = null,
    @SerialName("epigrafe")
    @Serializable(with = EpigrafeListSerializer::class)
    val epigrafe: List<BoeEpigrafeDto> = emptyList(),
    @SerialName("item")
    @Serializable(with = ItemListSerializer::class)
    val item: List<BoeSumarioItemDto> = emptyList(),
    @SerialName("texto")
    val texto: BoeTextoDto? = null,
)

/**
 * Envoltorio `departamento.texto` de las Secciones IV (Justicia) y V.C (anuncios
 * particulares): agrupa las entradas bajo `texto.item` en lugar de exponerlas
 * directamente en `departamento.item`.
 */
@Serializable
data class BoeTextoDto(
    @SerialName("item")
    @Serializable(with = ItemListSerializer::class)
    val item: List<BoeSumarioItemDto> = emptyList(),
)

@Serializable
data class BoeEpigrafeDto(
    @SerialName("nombre")
    val nombre: String? = null,
    @SerialName("item")
    @Serializable(with = ItemListSerializer::class)
    val item: List<BoeSumarioItemDto> = emptyList(),
)

@Serializable
data class BoeSumarioItemDto(
    @SerialName("identificador")
    val identificador: String? = null,
    @SerialName("control")
    val control: String? = null,
    @SerialName("titulo")
    val titulo: String? = null,
    @SerialName("url_html")
    val urlHtml: String? = null,
    @SerialName("url_xml")
    val urlXml: String? = null,
    @SerialName("url_pdf")
    val urlPdf: BoeUrlPdfDto? = null,
)

@Serializable
data class BoeUrlPdfDto(
    @SerialName("szBytes")
    val szBytes: String? = null,
    @SerialName("szKBytes")
    val szKBytes: String? = null,
    @SerialName("pagina_inicial")
    val paginaInicial: String? = null,
    @SerialName("pagina_final")
    val paginaFinal: String? = null,
    @SerialName("texto")
    val texto: String? = null,
)
