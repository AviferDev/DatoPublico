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
import es.aviferdev.datopublico.backend.rag.generation.OpenCodeConfig
import es.aviferdev.datopublico.backend.rag.generation.OpenCodeTextGenerationProvider
import es.aviferdev.datopublico.backend.rag.generation.openCodeHttpClient
import es.aviferdev.datopublico.backend.rag.indexing.FragmentIndexer
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.backend.rag.summary.CitizenSummaryGenerator
import io.ktor.client.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import javax.sql.DataSource
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Entrada de línea de comandos de la **evaluación de resúmenes** one-shot
 * (`:backend:evaluateSummary`, FT00024).
 *
 * Carga el golden set versionado de publicaciones con resumen de referencia,
 * resuelve la conexión (`POSTGRES_*`), la clave/config de OpenCode
 * (`OPENCODE_API_KEY`, fail-fast) y las rutas del modelo E5
 * (`EMBEDDING_MODEL_PATH`/`EMBEDDING_TOKENIZER_PATH` o el directorio por defecto),
 * indexa el corpus del propio dataset ([FragmentIndexer], idempotente), genera y
 * evalúa cada publicación con [CitizenSummaryGenerator] (OpenCode) contra su
 * referencia y registra el resultado (log JSON estructurado + resumen legible).
 * Un fallo de configuración, de base de datos, de modelo o de generación sale con
 * código ≠ 0 y mensaje claro; el pool, el modelo y el cliente se cierran en
 * `finally`. **No** se cablea en el arranque del servidor: se lanza a mano con su
 * tarea Gradle (opt-in).
 */
fun main() {
    val logger = LoggerFactory.getLogger(EVALUATION_LOGGER)
    val exitCode = try {
        runBlocking { runEvaluation(logger) }
        0
    } catch (error: Exception) {
        logger.error("Evaluación de resúmenes abortada: {}", error.message)
        1
    }
    exitProcess(exitCode)
}

/**
 * Carga el dataset y la configuración, construye el pool y evalúa, cerrando el pool.
 *
 * @throws IllegalArgumentException si el dataset no es válido.
 * @throws es.aviferdev.datopublico.backend.rag.generation.TextGenerationException
 *   si falta `OPENCODE_API_KEY` (fail-fast antes de tocar la base de datos).
 */
private suspend fun runEvaluation(logger: Logger) {
    val dataset = SummaryGoldenSetLoader.load(System.getenv(DATASET_PATH_KEY))
    val config = OpenCodeConfig.fromEnv()
    val dataSource = Database.createDataSource(DatabaseConfig.fromEnv())
    try {
        evaluateWithProvider(logger, dataset, config, dataSource)
    } finally {
        (dataSource as? AutoCloseable)?.close()
    }
}

/** Construye el proveedor E5 real y el cliente OpenCode, cerrando ambos al terminar. */
private suspend fun evaluateWithProvider(
    logger: Logger,
    dataset: SummaryGoldenSet,
    config: OpenCodeConfig,
    dataSource: DataSource,
) {
    val provider = createProvider()
    try {
        generateAndEvaluate(logger, dataset, config, dataSource, provider)
    } finally {
        provider.close()
    }
}

/** Compone el generador, indexa el corpus del dataset y evalúa cada entrada. */
private suspend fun generateAndEvaluate(
    logger: Logger,
    dataset: SummaryGoldenSet,
    config: OpenCodeConfig,
    dataSource: DataSource,
    provider: EmbeddingProvider,
) {
    val client = openCodeHttpClient()
    try {
        val repository = FragmentRepositoryJdbc(dataSource)
        val generator = createGenerator(config, client, provider, repository)
        SummaryEvaluationLog.logStarted(logger, dataset.version, dataset.entries.size)
        indexCorpus(dataSource, dataset, provider, repository)
        val result = evaluate(dataset, generator)
        SummaryEvaluationLog.logCompleted(logger, result, dataset.version)
        printSummary(result)
    } finally {
        client.close()
    }
}

/** Construye el generador ciudadano real (recuperación E5 + generación OpenCode). */
private fun createGenerator(
    config: OpenCodeConfig,
    client: HttpClient,
    provider: EmbeddingProvider,
    repository: FragmentRepository,
): CitizenSummaryGenerator = CitizenSummaryGenerator(
    hybridSearch = HybridSearch(provider, repository),
    textGenerationProvider = OpenCodeTextGenerationProvider(client, config),
)

/** Carga el proveedor E5 real desde las rutas resueltas (fail-fast si faltan). */
private fun createProvider(): E5EmbeddingProvider = E5EmbeddingProvider.from(
    resolveArtifact(EmbeddingConfig.MODEL_PATH_KEY, EMBEDDING_MODEL_FILE),
    resolveArtifact(EmbeddingConfig.TOKENIZER_PATH_KEY, EMBEDDING_TOKENIZER_FILE),
)

/** Persiste cada publicación del corpus y luego indexa sus fragmentos. */
private fun indexCorpus(
    dataSource: DataSource,
    dataset: SummaryGoldenSet,
    provider: EmbeddingProvider,
    repository: FragmentRepository,
): Int {
    val publications = PublicationRepositoryJdbc(dataSource)
    val indexer = FragmentIndexer(provider, repository)
    return dataset.entries.sumOf { entry ->
        publications.save(entry.publication)
        indexer.index(entry.publication)
    }
}

/** Evalúa el dataset delegando cada entrada en el generador (adaptador [SummaryGenerator]). */
private suspend fun evaluate(
    dataset: SummaryGoldenSet,
    generator: CitizenSummaryGenerator,
): SummaryQualityResult {
    val summaryGenerator = SummaryGenerator { publication, limit ->
        generator.generate(publication, limit)
    }
    return SummaryQualityEvaluator(summaryGenerator).evaluate(dataset)
}

/** Imprime el resumen legible: una línea por entrada y la línea agregada. */
private fun printSummary(result: SummaryQualityResult) {
    result.perEntry.forEach { row ->
        println(
            "entry=${row.entryId} what_changes_f1=${formatMetric(row.whatChangesF1)} " +
                "who_is_affected_f1=${formatMetric(row.whoIsAffectedF1)} " +
                "figures_recall=${formatMetric(row.figuresRecall)} hit=${row.hit} invalid=${row.invalid}"
        )
    }
    println(
        "entries=${result.entryCount} mean_what_changes_f1=${formatMetric(result.meanWhatChangesF1)} " +
            "mean_who_is_affected_f1=${formatMetric(result.meanWhoIsAffectedF1)} " +
            "mean_figures_recall=${formatMetric(result.meanFiguresRecall)} " +
            "hit_rate=${formatMetric(result.hitRate)} invalid=${result.invalidCount}"
    )
}

/** Formatea una métrica con 4 decimales, independiente del *locale*. */
private fun formatMetric(value: Double): String = String.format(Locale.ROOT, "%.4f", value)

/**
 * Resuelve la ruta del artefacto [fileName]: la variable de entorno [envKey] o el
 * directorio por defecto del modelo.
 *
 * El primer candidato es el valor de [envKey] (si está definido y no vacío); si no
 * existe como fichero, se prueban los directorios por defecto.
 *
 * @throws EmbeddingException si no se encuentra el artefacto (fail-fast).
 */
private fun resolveArtifact(envKey: String, fileName: String): Path {
    val configured = System.getenv(envKey)?.trim()?.takeIf { value -> value.isNotEmpty() }
    val candidates = listOfNotNull(configured?.let { value -> Path.of(value) })
        .plus(DEFAULT_MODEL_DIRS.map { dir -> dir.resolve(fileName) })
    return candidates.firstOrNull { path -> Files.isRegularFile(path) }
        ?: throw EmbeddingException(
            "No se encontró '$fileName': define $envKey o descárgalo con " +
                "backend/tools/download-embedding-model.sh."
        )
}

/** Nombre del logger de la herramienta. */
private const val EVALUATION_LOGGER = "es.aviferdev.datopublico.backend.rag.evaluation"

/** Ruta alternativa del golden set de resúmenes (fichero, no recurso). */
private const val DATASET_PATH_KEY = "SUMMARY_EVAL_DATASET"

/** Nombre del fichero ONNX int8 del modelo E5. */
private const val EMBEDDING_MODEL_FILE = "model.onnx"

/** Nombre del fichero del tokenizador XLM-R. */
private const val EMBEDDING_TOKENIZER_FILE = "tokenizer.json"

/** Directorios por defecto del modelo, según el directorio de trabajo del proceso. */
private val DEFAULT_MODEL_DIRS = listOf(
    Path.of("models/multilingual-e5-small"),
    Path.of("backend/models/multilingual-e5-small"),
)
