package es.aviferdev.datopublico.backend.ingesta.publicacion

import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.PlazoDto
import es.aviferdev.datopublico.model.SeccionBoeDto

/**
 * Publicación del BOE ya enriquecida con el **texto oficial** de la fuente.
 *
 * Es el modelo **interno** del backend (ni serializable ni `Dto`): lo consumirán
 * la persistencia (FT00008), el job de ingesta (FT00009) y la clasificación
 * (FT00012). No es el contrato de `:shared` ([es.aviferdev.datopublico.model.PublicacionDto]);
 * añade el texto completo, las URLs xml/pdf y el rango.
 *
 * [fechaPublicacion] es una fecha ISO-8601 (`2026-10-09`) como `String`, coherente
 * con `EntradaSumario` y `PublicacionDto`. [organismo], [epigrafe], [texto],
 * [urlXml], [urlPdf] y [rango] son opcionales porque la estructura del BOE varía
 * entre secciones y hay publicaciones sin texto XML disponible (imagen/PDF).
 *
 * [rango] procede del XML estructurado (`xml.php`) y es útil para los destacados
 * (FT00013). El resto de metadatos enriquecidos del XML (`departamento`,
 * `fechaDisposicion`, `numeroOficial`, `origenLegislativo`) se conservan en
 * [DocumentoBoe.metadatos] para que la persistencia decida qué guarda.
 *
 * [categoria] y [plazo] los rellena [PublicacionParser] al construir el modelo
 * (FT00012): la categoría es siempre una de las 11 curadas y el plazo queda
 * `null` cuando la fuente no lo indica de forma fiable.
 */
data class Publicacion(
    /** Identificador oficial del BOE (p. ej. `BOE-A-2026-20979`). */
    val id: String,
    /** Título oficial de la publicación. */
    val titulo: String,
    /** Fecha de publicación ISO-8601 (`2026-10-09`). */
    val fechaPublicacion: String,
    /** Organismo emisor (del sumario o, en su defecto, del XML); opcional. */
    val organismo: String?,
    /** Sección del BOE de la que procede. */
    val seccion: SeccionBoeDto,
    /** Epígrafe del BOE; opcional (falta en las entradas sin epígrafe). */
    val epigrafe: String?,
    /** Texto completo normalizado por párrafos; `null` si la fuente no lo trae. */
    val texto: String?,
    /** URL oficial de la publicación (html o, si no existe, pdf). */
    val urlOficial: String,
    /** URL del XML estructurado (`xml.php`); opcional. */
    val urlXml: String?,
    /** URL del PDF oficial; opcional. */
    val urlPdf: String?,
    /** Rango normativo del XML (`Real Decreto`, `Orden`…); opcional. */
    val rango: String?,
    /** Categoría curada (FT00012); la asigna [PublicacionParser], nunca `null`. */
    val categoria: CategoriaDto? = null,
    /**
     * Plazo de solicitud (convocatorias de empleo público, becas o subvenciones);
     * lo extrae [PublicacionParser] cuando es fiable, o `null`. Es aditivo para
     * que la persistencia (FT00008) pueda guardar y recuperar el plazo.
     */
    val plazo: PlazoDto? = null,
)
