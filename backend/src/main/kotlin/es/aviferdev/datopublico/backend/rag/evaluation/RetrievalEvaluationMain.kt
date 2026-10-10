package es.aviferdev.datopublico.backend.rag.evaluation

import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.backend.persistence.FragmentRepository
import es.aviferdev.datopublico.backend.persistence.FragmentRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.PublicationRepositoryJdbc
import es.aviferdev.datopublico.backend.rag.embeddings.E5EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingConfig
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingException
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.indexing.FragmentIndexer
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import javax.sql.DataSource
import kotlin.system.exitProcess
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Entrada de línea de comandos de la **evaluación de recuperación** one-shot
 * (`:backend:evaluateRetrieval`, FT00018).
 *
 * Carga el golden set versionado, resuelve la conexión (`POSTGRES_*`) y las rutas
 * del modelo E5 (`EMBEDDING_MODEL_PATH`/`EMBEDDING_TOKENIZER_PATH` o el
 * directorio por defecto), indexa el corpus del propio dataset
 * ([FragmentIndexer], idempotente), evalúa cada consulta con [HybridSearch] y
 * registra el resultado (log JSON estructurado + resumen legible). Un fallo de
 * configuración, de base de datos o de modelo sale con código ≠ 0 y mensaje
 * claro; el pool y el modelo se cierran en `finally`. **No** se cablea en el
 * arranque del servidor: se lanza a mano con su tarea Gradle.
 */
fun main() {
    val logger = LoggerFactory.getLogger(EVALUATION_LOGGER)
    val exitCode = try {
        runEvaluation(logger)
        0
    } catch (error: Exception) {
        logger.error("Evaluación de recuperación abortada: {}", error.message)
        1
    }
    exitProcess(exitCode)
}

/**
 * Carga el dataset, construye el pool y ejecuta la evaluación, cerrando el pool.
 *
 * @throws IllegalArgumentException si el dataset no es válido o una variable de
 *   entorno tiene un valor inválido (fail-fast antes de indexar).
 */
private fun runEvaluation(logger: Logger) {
    val dataset = RetrievalGoldenSetLoader.load(System.getenv(DATASET_PATH_KEY))
    val k = resolveK(dataset)
    val dataSource = Database.createDataSource(DatabaseConfig.fromEnv())
    try {
        evaluateWithResources(logger, dataset, k, dataSource)
    } finally {
        (dataSource as? AutoCloseable)?.close()
    }
}

/** Construye el proveedor real, indexa el corpus y evalúa, cerrando el modelo. */
private fun evaluateWithResources(
    logger: Logger,
    dataset: RetrievalGoldenSet,
    k: Int,
    dataSource: DataSource,
) {
    val provider = createProvider()
    try {
        val repository = FragmentRepositoryJdbc(dataSource)
        RetrievalEvaluationLog.logStarted(logger, dataset.version, k, dataset.queries.size)
        indexCorpus(dataSource, dataset, provider, repository)
        val result = evaluate(dataset, k, provider, repository)
        RetrievalEvaluationLog.logCompleted(logger, result, dataset.version)
        printSummary(result)
    } finally {
        provider.close()
    }
}

/** Carga el proveedor E5 real desde las rutas resueltas (fail-fast si faltan). */
private fun createProvider(): E5EmbeddingProvider = E5EmbeddingProvider.from(
    resolveArtifact(EmbeddingConfig.MODEL_PATH_KEY, MODEL_FILE),
    resolveArtifact(EmbeddingConfig.TOKENIZER_PATH_KEY, TOKENIZER_FILE),
)

/** Persiste cada publicación del corpus y luego indexa sus fragmentos. */
private fun indexCorpus(
    dataSource: DataSource,
    dataset: RetrievalGoldenSet,
    provider: EmbeddingProvider,
    repository: FragmentRepository,
): Int {
    val publications = PublicationRepositoryJdbc(dataSource)
    val indexer = FragmentIndexer(provider, repository)
    return dataset.corpus.sumOf { publication ->
        publications.save(publication)
        indexer.index(publication)
    }
}

/** Evalúa el dataset delegando cada consulta en [HybridSearch] (adaptador [Retriever]). */
private fun evaluate(
    dataset: RetrievalGoldenSet,
    k: Int,
    provider: EmbeddingProvider,
    repository: FragmentRepository,
): RetrievalEvaluationResult {
    val search = HybridSearch(provider, repository)
    val evaluator = RetrievalEvaluator { query, filter, limit -> search.search(query, filter, limit) }
    return evaluator.evaluate(dataset, k)
}

/** Imprime el resumen legible: una línea por consulta y la línea agregada. */
private fun printSummary(result: RetrievalEvaluationResult) {
    result.perQuery.forEach { row ->
        println(
            "query=${row.queryId} precision=${format(row.precision)} " +
                "recall=${format(row.recall)} mrr=${format(row.reciprocalRank)}"
        )
    }
    println(
        "k=${result.k} queries=${result.queryCount} " +
            "mean_precision=${format(result.meanPrecision)} " +
            "mean_recall=${format(result.meanRecall)} mean_mrr=${format(result.meanMrr)}"
    )
}

/** Formatea una métrica con 4 decimales, independiente del *locale*. */
private fun format(value: Double): String = String.format(Locale.ROOT, "%.4f", value)

/**
 * Resuelve la ventana de evaluación: `RETRIEVAL_EVAL_K` si está definida, o la del
 * dataset.
 *
 * @throws IllegalArgumentException si la variable no es un entero `>= 1`.
 */
private fun resolveK(dataset: RetrievalGoldenSet): Int {
    val raw = System.getenv(K_OVERRIDE_KEY)?.trim()?.takeIf { value -> value.isNotBlank() }
    val value = raw?.toIntOrNull()
    require(raw == null || (value != null && value >= 1)) {
        "Valor inválido de $K_OVERRIDE_KEY: '$raw'. Usa un entero mayor o igual que 1."
    }
    return value ?: dataset.k
}

/**
 * Resuelve la ruta del artefacto [fileName]: la variable de entorno [envKey] o el
 * directorio por defecto del modelo.
 *
 * @throws EmbeddingException si no se encuentra el artefacto (fail-fast).
 */
private fun resolveArtifact(envKey: String, fileName: String): Path {
    val fromEnv = System.getenv(envKey)
        ?.trim()
        ?.takeIf { value -> value.isNotBlank() }
        ?.let { value -> Path.of(value) }
    val fromDefault = DEFAULT_MODEL_DIRS
        .map { dir -> dir.resolve(fileName) }
        .firstOrNull { path -> Files.isRegularFile(path) }
    return fromEnv ?: fromDefault ?: throw EmbeddingException(
        "No se encontró '$fileName': define $envKey o descárgalo con " +
            "backend/tools/download-embedding-model.sh."
    )
}

/** Nombre del logger de la herramienta. */
private const val EVALUATION_LOGGER = "es.aviferdev.datopublico.backend.rag.evaluation"

/** Ruta alternativa del golden set (fichero, no recurso). */
private const val DATASET_PATH_KEY = "RETRIEVAL_EVAL_DATASET"

/** Override de la ventana de evaluación (`k`). */
private const val K_OVERRIDE_KEY = "RETRIEVAL_EVAL_K"

/** Nombre del fichero ONNX int8 del modelo E5. */
private const val MODEL_FILE = "model.onnx"

/** Nombre del fichero del tokenizador XLM-R. */
private const val TOKENIZER_FILE = "tokenizer.json"

/** Directorios por defecto del modelo, según el directorio de trabajo del proceso. */
private val DEFAULT_MODEL_DIRS = listOf(
    Path.of("models/multilingual-e5-small"),
    Path.of("backend/models/multilingual-e5-small"),
)
