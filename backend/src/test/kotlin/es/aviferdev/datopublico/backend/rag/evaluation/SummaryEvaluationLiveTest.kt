package es.aviferdev.datopublico.backend.rag.evaluation

import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.backend.persistence.FragmentRepository
import es.aviferdev.datopublico.backend.persistence.FragmentRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.PublicationRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.executeUpdate
import es.aviferdev.datopublico.backend.rag.embeddings.E5EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingConfig
import es.aviferdev.datopublico.backend.rag.generation.OpenCodeConfig
import es.aviferdev.datopublico.backend.rag.generation.OpenCodeTextGenerationProvider
import es.aviferdev.datopublico.backend.rag.generation.openCodeHttpClient
import es.aviferdev.datopublico.backend.rag.indexing.FragmentIndexer
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.backend.rag.summary.CitizenSummaryGenerator
import io.ktor.client.HttpClient
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest

/**
 * Prueba **opt-in** de extremo a extremo (escenario 7 del spec): indexa el corpus
 * del golden set versionado con el **modelo E5 real**, genera el resumen de cada
 * publicación con [CitizenSummaryGenerator] (OpenCode) y afirma un **umbral
 * conservador y documentado** sobre las métricas agregadas.
 *
 * Requiere **las tres** condiciones: `OPENCODE_LIVE_TEST=1` (generación real),
 * `DB_LIVE_TEST=1` (PostgreSQL migrado de `docker-compose`) y
 * `EMBEDDING_LIVE_TEST=1` (modelo E5 descargado en `backend/models/`). Sin ellas se
 * omite sin abrir conexión, cargar el modelo ni invocar al proveedor:
 *
 * ```
 * backend/tools/download-embedding-model.sh
 * cp .env.example .env && docker compose up -d
 * ./gradlew :backend:flywayMigrate
 * OPENCODE_API_KEY=… OPENCODE_LIVE_TEST=1 DB_LIVE_TEST=1 EMBEDDING_LIVE_TEST=1 \
 *   ./gradlew :backend:test --tests '*SummaryEvaluationLiveTest'
 * ```
 */
class SummaryEvaluationLiveTest {

    @Test
    fun `indexes the golden set and evaluates the citizen summaries`() = runTest(timeout = LIVE_TIMEOUT) {
        val artifacts = resolveArtifacts()
        if (isLiveEnabled(artifacts)) {
            runEvaluation(artifacts!!)
        } else {
            println(
                "SummaryEvaluationLiveTest omitido: exporta OPENCODE_LIVE_TEST=1, DB_LIVE_TEST=1 y " +
                    "EMBEDDING_LIVE_TEST=1 (con el modelo descargado y OPENCODE_API_KEY) para ejecutarlo."
            )
        }
    }

    /** `true` si están las tres variables opt-in y el modelo E5 está disponible. */
    private fun isLiveEnabled(artifacts: Pair<Path, Path>?): Boolean =
        System.getenv("OPENCODE_LIVE_TEST") == "1" &&
            System.getenv("DB_LIVE_TEST") == "1" &&
            System.getenv("EMBEDDING_LIVE_TEST") == "1" &&
            artifacts != null

    /** Indexa el corpus, genera y evalúa cada resumen con limpieza final. */
    private suspend fun runEvaluation(artifacts: Pair<Path, Path>) {
        val environment = environment()
        val dataSource = Database.createDataSource(DatabaseConfig.fromEnv(environment))
        val provider = E5EmbeddingProvider.from(artifacts.first, artifacts.second)
        val client = openCodeHttpClient()
        try {
            deleteTestData(dataSource)
            val dataset = SummaryGoldenSetLoader.load()
            val repository = FragmentRepositoryJdbc(dataSource)
            indexCorpus(dataSource, dataset, provider, repository)
            val result = evaluate(dataset, provider, repository, client, environment)
            printSummary(result)
            assertConservativeThreshold(result)
        } finally {
            deleteTestData(dataSource)
            client.close()
            provider.close()
            (dataSource as AutoCloseable).close()
        }
    }

    /** Persiste las publicaciones del corpus y las indexa con el proveedor real. */
    private fun indexCorpus(
        dataSource: DataSource,
        dataset: SummaryGoldenSet,
        provider: E5EmbeddingProvider,
        repository: FragmentRepository,
    ) {
        val publications = PublicationRepositoryJdbc(dataSource)
        val indexer = FragmentIndexer(provider, repository)
        dataset.entries.forEach { entry ->
            publications.save(entry.publication)
            indexer.index(entry.publication)
        }
    }

    /** Genera y evalúa el dataset con el generador ciudadano real (OpenCode). */
    private suspend fun evaluate(
        dataset: SummaryGoldenSet,
        provider: E5EmbeddingProvider,
        repository: FragmentRepository,
        client: HttpClient,
        environment: Map<String, String>,
    ): SummaryQualityResult {
        val generator = CitizenSummaryGenerator(
            hybridSearch = HybridSearch(provider, repository),
            textGenerationProvider = OpenCodeTextGenerationProvider(client, OpenCodeConfig.fromEnv(environment)),
        )
        val summaryGenerator = SummaryGenerator { publication, limit ->
            generator.generate(publication, limit)
        }
        return SummaryQualityEvaluator(summaryGenerator).evaluate(dataset)
    }

    /** Imprime las métricas reales por entrada y las agregadas, para la evidencia. */
    private fun printSummary(result: SummaryQualityResult) {
        result.perEntry.forEach { row ->
            println(
                "SummaryEvaluationLiveTest entry=${row.entryId} what_changes_f1=${row.whatChangesF1} " +
                    "who_is_affected_f1=${row.whoIsAffectedF1} figures_recall=${row.figuresRecall} " +
                    "hit=${row.hit} invalid=${row.invalid}"
            )
        }
        println(
            "SummaryEvaluationLiveTest mean entries=${result.entryCount} " +
                "what_changes_f1=${result.meanWhatChangesF1} who_is_affected_f1=${result.meanWhoIsAffectedF1} " +
                "figures_recall=${result.meanFiguresRecall} hit_rate=${result.hitRate} invalid=${result.invalidCount}"
        )
    }

    /** Umbral conservador y documentado: no exige perfección (corpus pequeño, léxico e int8). */
    private fun assertConservativeThreshold(result: SummaryQualityResult) {
        assertTrue(
            result.meanWhatChangesF1 >= MIN_WHAT_CHANGES_F1,
            "F1 medio de «¿Qué cambia?» por debajo de $MIN_WHAT_CHANGES_F1: ${result.meanWhatChangesF1}",
        )
        assertTrue(
            result.hitRate >= MIN_HIT_RATE,
            "tasa de acierto por debajo de $MIN_HIT_RATE: ${result.hitRate}",
        )
    }

    /** Borra las publicaciones de evaluación (el `ON DELETE CASCADE` limpia sus fragmentos). */
    private fun deleteTestData(dataSource: DataSource) {
        dataSource.executeUpdate("DELETE FROM publicacion WHERE id LIKE ?") { statement ->
            statement.setString(1, "$PREFIX%")
        }
    }

    /** Resuelve las rutas del modelo/tokenizador desde el entorno o el directorio por defecto. */
    private fun resolveArtifacts(): Pair<Path, Path>? {
        val modelEnv = System.getenv(EmbeddingConfig.MODEL_PATH_KEY)
        val tokenizerEnv = System.getenv(EmbeddingConfig.TOKENIZER_PATH_KEY)
        val fromEnv = modelEnv?.let { model ->
            tokenizerEnv?.let { tokenizer -> Path.of(model) to Path.of(tokenizer) }
        }
        val defaultDir = DEFAULT_DIRS.firstOrNull { dir ->
            Files.isRegularFile(dir.resolve(MODEL_FILE)) && Files.isRegularFile(dir.resolve(TOKENIZER_FILE))
        }
        return fromEnv ?: defaultDir?.let { dir -> dir.resolve(MODEL_FILE) to dir.resolve(TOKENIZER_FILE) }
    }

    /** Entorno del test: variables del proceso más, si falta alguna, las de la `.env` de la raíz. */
    private fun environment(): Map<String, String> {
        val environment = System.getenv().toMutableMap()
        val dotEnv = listOf(File(".env"), File("../.env")).firstOrNull { file -> file.isFile }
        dotEnv?.readLines()
            ?.asSequence()
            ?.map { line -> line.trim() }
            ?.filter { line -> line.isNotEmpty() && !line.startsWith("#") && line.contains('=') }
            ?.forEach { line ->
                val key = line.substringBefore('=').trim()
                environment.putIfAbsent(key, line.substringAfter('=').trim().trim('"', '\''))
            }
        return environment
    }

    private companion object {
        const val MODEL_FILE = "model.onnx"
        const val TOKENIZER_FILE = "tokenizer.json"
        const val PREFIX = "EVAL-S"

        /** Umbral conservador del F1 medio de «¿Qué cambia?». */
        const val MIN_WHAT_CHANGES_F1 = 0.3

        /** Umbral conservador de la tasa de acierto. */
        const val MIN_HIT_RATE = 0.3

        /** Tiempo máximo del test live (la generación real es secuencial y con red). */
        val LIVE_TIMEOUT: Duration = 10.minutes

        /** Rutas relativas posibles según el directorio de trabajo del test. */
        val DEFAULT_DIRS = listOf(
            Path.of("models/multilingual-e5-small"),
            Path.of("backend/models/multilingual-e5-small"),
        )
    }
}
