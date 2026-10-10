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
 */
@Serializable
data class BoeSumarioResponseDto(
    val status: BoeStatusDto? = null,
    val data: BoeDataDto? = null,
)

@Serializable
data class BoeStatusDto(
    val code: String? = null,
    val text: String? = null,
)

@Serializable
data class BoeDataDto(
    val sumario: BoeSumarioDto? = null,
)

@Serializable
data class BoeSumarioDto(
    val metadatos: BoeMetadatosDto? = null,
    @Serializable(with = DiarioListSerializer::class)
    val diario: List<BoeDiarioDto> = emptyList(),
)

@Serializable
data class BoeMetadatosDto(
    val publicacion: String? = null,
    @SerialName("fecha_publicacion")
    val fechaPublicacion: String? = null,
)

@Serializable
data class BoeDiarioDto(
    val numero: String? = null,
    @Serializable(with = SeccionListSerializer::class)
    val seccion: List<BoeSeccionDto> = emptyList(),
    @SerialName("sumario_diario")
    val sumarioDiario: BoeSumarioDiarioDto? = null,
)

@Serializable
data class BoeSumarioDiarioDto(
    val identificador: String? = null,
    @SerialName("url_pdf")
    val urlPdf: BoeUrlPdfDto? = null,
)

@Serializable
data class BoeSeccionDto(
    val codigo: String? = null,
    val nombre: String? = null,
    @Serializable(with = DepartamentoListSerializer::class)
    val departamento: List<BoeDepartamentoDto> = emptyList(),
)

@Serializable
data class BoeDepartamentoDto(
    val codigo: String? = null,
    val nombre: String? = null,
    @Serializable(with = EpigrafeListSerializer::class)
    val epigrafe: List<BoeEpigrafeDto> = emptyList(),
    @Serializable(with = ItemListSerializer::class)
    val item: List<BoeSumarioItemDto> = emptyList(),
)

@Serializable
data class BoeEpigrafeDto(
    val nombre: String? = null,
    @Serializable(with = ItemListSerializer::class)
    val item: List<BoeSumarioItemDto> = emptyList(),
)

@Serializable
data class BoeSumarioItemDto(
    val identificador: String? = null,
    val control: String? = null,
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
    val texto: String? = null,
    @SerialName("pagina_inicial")
    val paginaInicial: String? = null,
    @SerialName("pagina_final")
    val paginaFinal: String? = null,
)
