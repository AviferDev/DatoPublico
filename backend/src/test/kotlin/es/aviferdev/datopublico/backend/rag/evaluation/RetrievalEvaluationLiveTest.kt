package es.aviferdev.datopublico.backend.rag.evaluation

import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.backend.persistence.FragmentRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.PublicationRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.executeUpdate
import es.aviferdev.datopublico.backend.rag.embeddings.E5EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingConfig
import es.aviferdev.datopublico.backend.rag.indexing.FragmentIndexer
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Prueba **opt-in** de extremo a extremo (escenario 6 del spec): indexa el corpus
 * del golden set versionado con el **modelo E5 real**, recupera cada consulta con
 * [HybridSearch] y afirma un **umbral conservador y documentado** sobre las
 * métricas agregadas.
 *
 * Requiere **las dos** variables: `DB_LIVE_TEST=1` (PostgreSQL migrado de
 * `docker-compose`) y `EMBEDDING_LIVE_TEST=1` (modelo E5 descargado en
 * `backend/models/`). Sin ellas se omite sin abrir conexión ni cargar el modelo:
 *
 * ```
 * backend/tools/download-embedding-model.sh
 * cp .env.example .env && docker compose up -d
 * ./gradlew :backend:flywayMigrate
 * DB_LIVE_TEST=1 EMBEDDING_LIVE_TEST=1 ./gradlew :backend:test --tests '*RetrievalEvaluationLiveTest'
 * ```
 */
class RetrievalEvaluationLiveTest {

    @Test
    fun `indexes the golden set with the real model and evaluates the retrieval`() {
        val artifacts = resolveArtifacts()
        if (System.getenv("DB_LIVE_TEST") != "1" || System.getenv("EMBEDDING_LIVE_TEST") != "1" || artifacts == null) {
            println(
                "RetrievalEvaluationLiveTest omitido: descarga el modelo con " +
                    "backend/tools/download-embedding-model.sh y exporta DB_LIVE_TEST=1 y EMBEDDING_LIVE_TEST=1."
            )
        } else {
            runEvaluation(artifacts)
        }
    }

    /** Indexa el corpus del golden set y evalúa contra PostgreSQL, con limpieza final. */
    private fun runEvaluation(artifacts: Pair<Path, Path>) {
        val dataSource = Database.createDataSource(DatabaseConfig.fromEnv(environment()))
        val provider = E5EmbeddingProvider.from(artifacts.first, artifacts.second)
        try {
            deleteTestData(dataSource)
            val repository = FragmentRepositoryJdbc(dataSource)
            val dataset = RetrievalGoldenSetLoader.load()
            indexCorpus(dataSource, dataset, provider, repository)
            val result = evaluate(dataset, provider, repository)
            printSummary(result)
            assertConservativeThreshold(result)
        } finally {
            deleteTestData(dataSource)
            provider.close()
            (dataSource as AutoCloseable).close()
        }
    }

    /** Persiste las publicaciones del corpus y las indexa con el proveedor real. */
    private fun indexCorpus(
        dataSource: DataSource,
        dataset: RetrievalGoldenSet,
        provider: E5EmbeddingProvider,
        repository: FragmentRepositoryJdbc,
    ) {
        val publications = PublicationRepositoryJdbc(dataSource)
        val indexer = FragmentIndexer(provider, repository)
        dataset.corpus.forEach { publication ->
            publications.save(publication)
            indexer.index(publication)
        }
    }

    /** Evalúa el dataset con `HybridSearch` y el `k` del propio golden set. */
    private fun evaluate(
        dataset: RetrievalGoldenSet,
        provider: E5EmbeddingProvider,
        repository: FragmentRepositoryJdbc,
    ): RetrievalEvaluationResult {
        val search = HybridSearch(provider, repository)
        val evaluator = RetrievalEvaluator { query, filter, limit -> search.search(query, filter, limit) }
        return evaluator.evaluate(dataset)
    }

    /** Imprime las métricas reales para la evidencia. */
    private fun printSummary(result: RetrievalEvaluationResult) {
        result.perQuery.forEach { row ->
            println(
                "RetrievalEvaluationLiveTest query=${row.queryId} " +
                    "precision=${row.precision} recall=${row.recall} mrr=${row.reciprocalRank}"
            )
        }
        println(
            "RetrievalEvaluationLiveTest mean k=${result.k} queries=${result.queryCount} " +
                "precision=${result.meanPrecision} recall=${result.meanRecall} mrr=${result.meanMrr}"
        )
    }

    /** Umbral conservador y documentado: no exige perfección (corpus pequeño e int8). */
    private fun assertConservativeThreshold(result: RetrievalEvaluationResult) {
        assertTrue(result.meanRecall >= MIN_RECALL, "recall@k medio por debajo de $MIN_RECALL: ${result.meanRecall}")
        assertTrue(result.meanMrr >= MIN_MRR, "MRR medio por debajo de $MIN_MRR: ${result.meanMrr}")
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
        const val PREFIX = "EVAL-"

        /** Umbral conservador del recall@k medio (corpus pequeño y modelo int8). */
        const val MIN_RECALL = 0.6

        /** Umbral conservador del MRR medio. */
        const val MIN_MRR = 0.5

        /** Rutas relativas posibles según el directorio de trabajo del test. */
        val DEFAULT_DIRS = listOf(
            Path.of("models/multilingual-e5-small"),
            Path.of("backend/models/multilingual-e5-small"),
        )
    }
}
