package es.aviferdev.datopublico.backend.ingestion.publication

import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.DeadlineDto
import es.aviferdev.datopublico.model.BoeSectionDto

/**
 * Publicación del BOE ya enriquecida con el **texto oficial** de la fuente.
 *
 * Es el modelo **interno** del backend (ni serializable ni `Dto`): lo consumirán
 * la persistencia (FT00008), el job de ingesta (FT00009) y la clasificación
 * (FT00012). No es el contrato de `:shared` ([es.aviferdev.datopublico.model.PublicationDto]);
 * añade el texto completo, las URLs xml/pdf y el rango.
 *
 * [fechaPublicacion] es una fecha ISO-8601 (`2026-10-09`) como `String`, coherente
 * con `SummaryEntry` y `PublicationDto`. [issuingBody], [heading], [body],
 * [urlXml], [urlPdf] y [rank] son opcionales porque la estructura del BOE varía
 * entre secciones y hay publicaciones sin texto XML disponible (imagen/PDF).
 *
 * [rank] procede del XML estructurado (`xml.php`) y es útil para los destacados
 * (FT00013). El resto de metadatos enriquecidos del XML (`departamento`,
 * `fechaDisposicion`, `numeroOficial`, `origenLegislativo`) se conservan en
 * [BoeDocument.metadata] para que la persistencia decida qué guarda.
 *
 * [category] y [deadline] los rellena [PublicationParser] al construir el modelo
 * (FT00012): la categoría es siempre una de las 11 curadas y el plazo queda
 * `null` cuando la fuente no lo indica de forma fiable.
 */
data class Publication(
    /** Identificador oficial del BOE (p. ej. `BOE-A-2026-20979`). */
    val id: String,
    /** Título oficial de la publicación. */
    val title: String,
    /** Fecha de publicación ISO-8601 (`2026-10-09`). */
    val publicationDate: String,
    /** Organismo emisor (del sumario o, en su defecto, del XML); opcional. */
    val issuingBody: String?,
    /** Sección del BOE de la que procede. */
    val section: BoeSectionDto,
    /** Epígrafe del BOE; opcional (falta en las entradas sin epígrafe). */
    val heading: String?,
    /** Texto completo normalizado por párrafos; `null` si la fuente no lo trae. */
    val body: String?,
    /** URL oficial de la publicación (html o, si no existe, pdf). */
    val officialUrl: String,
    /** URL del XML estructurado (`xml.php`); opcional. */
    val xmlUrl: String?,
    /** URL del PDF oficial; opcional. */
    val pdfUrl: String?,
    /** Rango normativo del XML (`Real Decreto`, `Orden`…); opcional. */
    val rank: String?,
    /** Categoría curada (FT00012); la asigna [PublicationParser], nunca `null`. */
    val category: CategoryDto? = null,
    /**
     * Plazo de solicitud (convocatorias de empleo público, becas o subvenciones);
     * lo extrae [PublicationParser] cuando es fiable, o `null`. Es aditivo para
     * que la persistencia (FT00008) pueda guardar y recuperar el plazo.
     */
    val deadline: DeadlineDto? = null,
)
