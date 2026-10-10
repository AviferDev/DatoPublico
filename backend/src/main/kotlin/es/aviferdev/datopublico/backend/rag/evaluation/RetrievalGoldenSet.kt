package es.aviferdev.datopublico.backend.rag.evaluation

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Golden set **versionado** de evaluación de la recuperación (FT00018): un corpus
 * modesto de publicaciones y una lista de consultas con sus fragmentos esperados.
 *
 * Es el **modelo interno** del backend (ni DTO ni `Entity`), por eso va **sin
 * sufijo** (`CONSTRAINTS.md` §«Nombres y contratos»). El corpus se mapea a
 * [Publicacion] para poder indexarlo con
 * [es.aviferdev.datopublico.backend.rag.indexing.FragmentIndexer] sin cambiar su
 * firma.
 *
 * @property version versión del dataset (documental, para trazabilidad).
 * @property k ventana por defecto de evaluación (`>= 1`).
 * @property corpus publicaciones del set de prueba (con su texto y metadatos).
 * @property queries consultas con su filtro y sus fragmentos relevantes.
 */
data class RetrievalGoldenSet(
    val version: String,
    val k: Int,
    val corpus: List<Publicacion>,
    val queries: List<RetrievalQuery>,
)

/**
 * Consulta del golden set ya validada y lista para evaluar.
 *
 * @property id identificador estable de la consulta (único en el dataset).
 * @property text texto de la consulta que se vectoriza.
 * @property filter filtro de metadatos de la recuperación híbrida.
 * @property relevant fragmentos esperados (referencias válidas dentro del corpus).
 */
data class RetrievalQuery(
    val id: String,
    val text: String,
    val filter: SearchFilter,
    val relevant: Set<FragmentRef>,
)

/**
 * Carga y **valida** el golden set de evaluación (FT00018).
 *
 * Es **puro** con respecto a la infraestructura: no abre conexión a base de datos
 * ni carga el modelo de embeddings. El dataset vive en los recursos de `main`
 * (`/rag/eval/golden-set.json`) porque lo consume la CLI
 * [RetrievalEvaluationMain]; [load] admite un fichero alternativo para operar con
 * otro dataset sin recompilar.
 *
 * La validación es **fail-fast** con `IllegalArgumentException` y mensaje claro:
 * `k >= 1`, corpus y consultas no vacíos, identificadores únicos y cada
 * [FragmentRef] relevante existente en el corpus (`publicationId` presente y
 * `order >= 0`).
 */
object RetrievalGoldenSetLoader {

    /** Ruta del recurso por defecto del golden set versionado. */
    const val DEFAULT_RESOURCE: String = "/rag/eval/golden-set.json"

    /**
     * Parsea y valida un golden set desde su [json].
     *
     * @throws IllegalArgumentException si el dataset no cumple las invariantes.
     * @throws kotlinx.serialization.SerializationException si el JSON no encaja
     *   con el contrato de los DTO.
     */
    fun parse(json: String): RetrievalGoldenSet {
        val dataset = DatoPublicoJson.decodeFromString<RetrievalGoldenSetDto>(json).toGoldenSet()
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
    fun load(path: String? = null): RetrievalGoldenSet =
        parse(if (path == null) readResource(DEFAULT_RESOURCE) else readFile(path))

    /** Lee el recurso de classpath [resource] como texto UTF-8. */
    private fun readResource(resource: String): String =
        RetrievalGoldenSetLoader::class.java.getResourceAsStream(resource)?.use { stream ->
            stream.readBytes().decodeToString()
        } ?: throw IllegalArgumentException("No se encontró el recurso del golden set: '$resource'.")

    /** Lee el fichero [path] como texto UTF-8 con fail-fast si no existe. */
    private fun readFile(path: String): String {
        val file = Path.of(path)
        require(Files.isRegularFile(file)) { "No existe el fichero del golden set: '$path'." }
        return Files.readString(file)
    }

    /** Comprueba las invariantes del dataset (fail-fast con mensaje claro). */
    private fun validate(dataset: RetrievalGoldenSet) {
        require(dataset.k >= 1) { "El golden set debe tener k >= 1, pero tiene ${dataset.k}." }
        require(dataset.corpus.isNotEmpty()) { "El golden set no puede tener el corpus vacío." }
        require(dataset.queries.isNotEmpty()) { "El golden set no puede tener consultas vacías." }
        val corpusIds = dataset.corpus.map { publication -> publication.id }
        require(corpusIds.toSet().size == corpusIds.size) {
            "El corpus del golden set tiene identificadores duplicados: $corpusIds."
        }
        val queryIds = dataset.queries.map { query -> query.id }
        require(queryIds.toSet().size == queryIds.size) {
            "Las consultas del golden set tienen identificadores duplicados: $queryIds."
        }
        val knownIds = corpusIds.toSet()
        dataset.queries.forEach { query -> validateRelevant(query, knownIds) }
    }

    /** Comprueba que cada fragmento relevante de [query] existe en el corpus. */
    private fun validateRelevant(query: RetrievalQuery, corpusIds: Set<String>) {
        query.relevant.forEach { ref ->
            require(ref.publicationId in corpusIds) {
                "La consulta '${query.id}' referencia una publicación inexistente: '${ref.publicationId}'."
            }
            require(ref.order >= 0) {
                "La consulta '${query.id}' referencia un order negativo: ${ref.order}."
            }
        }
    }
}

/** Golden set tal y como aparece en el JSON (`version`, `k`, `corpus`, `queries`). */
@Serializable
data class RetrievalGoldenSetDto(
    @SerialName("version") val version: String,
    @SerialName("k") val k: Int,
    @SerialName("corpus") val corpus: List<RetrievalCorpusItemDto>,
    @SerialName("queries") val queries: List<RetrievalQueryDto>,
)

/** Publicación del corpus del golden set (texto y metadatos de filtrado). */
@Serializable
data class RetrievalCorpusItemDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("date") val date: String,
    @SerialName("section") val section: SeccionBoeDto,
    @SerialName("category") val category: CategoriaDto,
    @SerialName("organization") val organization: String? = null,
    @SerialName("text") val text: String,
)

/** Consulta del golden set con su filtro y sus fragmentos relevantes. */
@Serializable
data class RetrievalQueryDto(
    @SerialName("id") val id: String,
    @SerialName("query") val query: String,
    @SerialName("filter") val filter: RetrievalFilterDto = RetrievalFilterDto(),
    @SerialName("relevant") val relevant: List<RetrievalRelevantDto>,
)

/** Filtro de metadatos de una consulta del golden set (todos opcionales). */
@Serializable
data class RetrievalFilterDto(
    @SerialName("publishedFrom") val publishedFrom: String? = null,
    @SerialName("publishedTo") val publishedTo: String? = null,
    @SerialName("category") val category: CategoriaDto? = null,
    @SerialName("section") val section: SeccionBoeDto? = null,
    @SerialName("organization") val organization: String? = null,
)

/** Referencia esperada de un fragmento relevante del corpus. */
@Serializable
data class RetrievalRelevantDto(
    @SerialName("publicationId") val publicationId: String,
    @SerialName("order") val order: Int,
)

/** Mapea el DTO del golden set a su modelo interno. */
private fun RetrievalGoldenSetDto.toGoldenSet(): RetrievalGoldenSet = RetrievalGoldenSet(
    version = version,
    k = k,
    corpus = corpus.map { item -> item.toPublicacion() },
    queries = queries.map { item -> item.toQuery() },
)

/**
 * Mapea un elemento del corpus a [Publicacion] reutilizando el modelo de dominio
 * existente; los campos no presentes en el dataset quedan `null` y la URL oficial
 * se deriva del `id`.
 */
private fun RetrievalCorpusItemDto.toPublicacion(): Publicacion = Publicacion(
    id = id,
    titulo = title,
    fechaPublicacion = date,
    organismo = organization,
    seccion = section,
    epigrafe = null,
    texto = text,
    urlOficial = "https://www.boe.es/diario_boe/txt.php?id=$id",
    urlXml = null,
    urlPdf = null,
    rango = null,
    categoria = category,
)

/** Mapea una consulta del DTO a su modelo interno [RetrievalQuery]. */
private fun RetrievalQueryDto.toQuery(): RetrievalQuery = RetrievalQuery(
    id = id,
    text = query,
    filter = filter.toSearchFilter(),
    relevant = relevant.map { item -> FragmentRef(item.publicationId, item.order) }.toSet(),
)

/** Mapea el filtro del DTO al [SearchFilter] de la recuperación híbrida. */
private fun RetrievalFilterDto.toSearchFilter(): SearchFilter = SearchFilter(
    publishedFrom = publishedFrom,
    publishedTo = publishedTo,
    category = category,
    section = section,
    organization = organization,
)
