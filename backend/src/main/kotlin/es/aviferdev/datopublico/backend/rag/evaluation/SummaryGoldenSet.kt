package es.aviferdev.datopublico.backend.rag.evaluation

import es.aviferdev.datopublico.backend.ingestion.publication.Publication
import es.aviferdev.datopublico.backend.rag.summary.SummaryGenerationDto
import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.BoeSectionDto
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Golden set **versionado** de evaluación de la calidad del resumen ciudadano
 * (FT00024): un corpus modesto de publicaciones con el **resumen de referencia**
 * de cada una.
 *
 * Es el **modelo interno** del backend (ni DTO ni `Entity`), por eso va **sin
 * sufijo** (`CONSTRAINTS.md` §«Nombres y contratos»). Cada entrada se mapea a
 * [Publication] para poder pasarla a
 * [es.aviferdev.datopublico.backend.rag.summary.CitizenSummaryGenerator] y medir
 * su resumen contra [SummaryGoldenSetEntry.reference].
 *
 * @property version versión del dataset (documental, para trazabilidad).
 * @property entries publicaciones con su resumen de referencia (no vacío).
 */
data class SummaryGoldenSet(
    val version: String,
    val entries: List<SummaryGoldenSetEntry>,
)

/**
 * Entrada del golden set ya validada y lista para evaluar.
 *
 * @property id identificador estable de la entrada (único en el dataset).
 * @property publication publicación de origen (con categoría y texto).
 * @property reference resumen de referencia con «¿Qué cambia?» y «¿A quién
 *   afecta?» no vacíos.
 */
data class SummaryGoldenSetEntry(
    val id: String,
    val publication: Publication,
    val reference: SummaryGenerationDto,
)

/**
 * Carga y **valida** el golden set de evaluación de resúmenes (FT00024).
 *
 * Es **puro** con respecto a la infraestructura: no abre conexión a base de datos,
 * no carga el modelo de embeddings y **no** invoca al proveedor de generación. El
 * dataset vive en los recursos de `main` (`/rag/eval/summary-golden-set.json`)
 * porque lo consume la CLI [SummaryEvaluationMain]; [load] admite un fichero
 * alternativo para operar con otro dataset sin recompilar.
 *
 * La validación es **fail-fast** con `IllegalArgumentException` (o su subclase
 * `SerializationException` si el JSON no encaja con los DTO) y mensaje claro:
 * `version` no vacía, `entries` no vacío, identificadores únicos, cada entrada con
 * categoría, título y texto no vacíos, y el resumen de referencia con «¿Qué
 * cambia?» y «¿A quién afecta?» no vacíos.
 */
object SummaryGoldenSetLoader {

    /** Ruta del recurso por defecto del golden set versionado. */
    const val DEFAULT_RESOURCE: String = "/rag/eval/summary-golden-set.json"

    /**
     * Parsea y valida un golden set desde su [json].
     *
     * @throws IllegalArgumentException si el dataset no cumple las invariantes.
     * @throws kotlinx.serialization.SerializationException si el JSON no encaja
     *   con el contrato de los DTO.
     */
    fun parse(json: String): SummaryGoldenSet {
        val dataset = DatoPublicoJson.decodeFromString<SummaryGoldenSetDto>(json).toGoldenSet()
        validate(dataset)
        return dataset
    }

    /**
     * Carga el golden set del [path] indicado o del recurso por defecto.
     *
     * @param path fichero alternativo; `null` usa [DEFAULT_RESOURCE].
     * @throws IllegalArgumentException si el recurso/fichero no existe o el
     *   dataset no es válido.
     */
    fun load(path: String? = null): SummaryGoldenSet =
        parse(if (path == null) readResource(DEFAULT_RESOURCE) else readFile(path))

    /** Lee el recurso de classpath [resource] como texto UTF-8. */
    private fun readResource(resource: String): String =
        SummaryGoldenSetLoader::class.java.getResourceAsStream(resource)?.use { stream ->
            stream.readBytes().decodeToString()
        } ?: throw IllegalArgumentException("No se encontró el recurso del golden set de resúmenes: '$resource'.")

    /** Lee el fichero [path] como texto UTF-8 con fail-fast si no existe. */
    private fun readFile(path: String): String {
        val file = Path.of(path)
        require(Files.isRegularFile(file)) { "No existe el fichero del golden set de resúmenes: '$path'." }
        return Files.readString(file)
    }

    /** Comprueba las invariantes del dataset (fail-fast con mensaje claro). */
    private fun validate(dataset: SummaryGoldenSet) {
        require(dataset.version.isNotBlank()) { "El golden set debe declarar una versión no vacía." }
        require(dataset.entries.isNotEmpty()) { "El golden set no puede tener entradas vacías." }
        val ids = dataset.entries.map { entry -> entry.id }
        require(ids.toSet().size == ids.size) {
            "El golden set tiene identificadores duplicados: $ids."
        }
        dataset.entries.forEach { entry -> validateEntry(entry) }
    }

    /** Comprueba que [entry] trae publicación y resumen de referencia utilizables. */
    private fun validateEntry(entry: SummaryGoldenSetEntry) {
        require(entry.publication.category != null) {
            "La entrada '${entry.id}' no tiene categoría."
        }
        require(entry.publication.title.isNotBlank()) {
            "La entrada '${entry.id}' no tiene título."
        }
        require(entry.publication.body.orEmpty().isNotBlank()) {
            "La entrada '${entry.id}' no tiene texto."
        }
        require(entry.reference.whatChanges.isNotBlank()) {
            "La referencia de '${entry.id}' no tiene «¿Qué cambia?»."
        }
        require(entry.reference.whoIsAffected.isNotBlank()) {
            "La referencia de '${entry.id}' no tiene «¿A quién afecta?»."
        }
    }
}

/** Golden set tal y como aparece en el JSON (`version`, `entries`). */
@Serializable
data class SummaryGoldenSetDto(
    @SerialName("version") val version: String,
    @SerialName("entries") val entries: List<SummaryGoldenSetEntryDto>,
)

/**
 * Publicación del golden set con su **resumen de referencia**.
 *
 * @property reference resumen de referencia; reutiliza [SummaryGenerationDto]
 *   (FT00020) porque es exactamente la forma del resumen (`queCambia`,
 *   `aQuienAfecta`, `cifrasClave` y `plazo`).
 */
@Serializable
data class SummaryGoldenSetEntryDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("date") val date: String,
    @SerialName("section") val section: BoeSectionDto,
    @SerialName("category") val category: CategoryDto,
    @SerialName("organization") val organization: String? = null,
    @SerialName("text") val text: String,
    @SerialName("reference") val reference: SummaryGenerationDto,
)

/** Mapea el DTO del golden set a su modelo interno. */
private fun SummaryGoldenSetDto.toGoldenSet(): SummaryGoldenSet = SummaryGoldenSet(
    version = version,
    entries = entries.map { item -> item.toEntry() },
)

/** Mapea una entrada del DTO a su modelo interno [SummaryGoldenSetEntry]. */
private fun SummaryGoldenSetEntryDto.toEntry(): SummaryGoldenSetEntry = SummaryGoldenSetEntry(
    id = id,
    publication = Publication(
        id = id,
        title = title,
        publicationDate = date,
        issuingBody = organization,
        section = section,
        heading = null,
        body = text,
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=$id",
        xmlUrl = null,
        pdfUrl = null,
        rank = null,
        category = category,
    ),
    reference = reference,
)
