package es.aviferdev.datopublico.backend.rag.indexing

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.backend.persistence.FragmentRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.PublicationRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.executeUpdate
import es.aviferdev.datopublico.backend.rag.embeddings.E5EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingConfig
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Prueba **opt-in** de extremo a extremo (escenario 8 del spec): indexa una
 * publicación con el **modelo E5 real** y la recupera con [HybridSearch],
 * comprobando que el fragmento relevante va primero y que el filtro de metadatos
 * se aplica.
 *
 * Requiere **las dos** variables: `DB_LIVE_TEST=1` (PostgreSQL migrado de
 * `docker-compose`) y `EMBEDDING_LIVE_TEST=1` (modelo E5 descargado en
 * `backend/models/`). Sin ellas se omite sin abrir conexión ni cargar el modelo:
 *
 * ```
 * backend/tools/download-embedding-model.sh
 * cp .env.example .env && docker compose up -d
 * ./gradlew :backend:flywayMigrate
 * DB_LIVE_TEST=1 EMBEDDING_LIVE_TEST=1 ./gradlew :backend:test --tests '*FragmentIndexerLiveTest'
 * ```
 */
class FragmentIndexerLiveTest {

    @Test
    fun `indexes with the real model and retrieves the relevant fragment`() {
        val artifacts = resolveArtifacts()
        if (System.getenv("DB_LIVE_TEST") != "1" || System.getenv("EMBEDDING_LIVE_TEST") != "1" || artifacts == null) {
            println(
                "FragmentIndexerLiveTest omitido: descarga el modelo con " +
                    "backend/tools/download-embedding-model.sh y exporta DB_LIVE_TEST=1 y EMBEDDING_LIVE_TEST=1."
            )
        } else {
            runEndToEnd(artifacts)
        }
    }

    /** Indexa con el modelo real y recupera contra PostgreSQL, con limpieza final. */
    private fun runEndToEnd(artifacts: Pair<Path, Path>) {
        val dataSource = Database.createDataSource(DatabaseConfig.fromEnv(environment()))
        val provider = E5EmbeddingProvider.from(artifacts.first, artifacts.second)
        try {
            deleteTestData(dataSource)
            val repository = FragmentRepositoryJdbc(dataSource)
            val count = indexPublication(dataSource, provider, repository)
            checkIndexedCount(count)
            checkRelevantFragmentFirst(provider, repository)
            checkMetadataFilter(provider, repository)
            checkStoredEmbedding(repository)
        } finally {
            deleteTestData(dataSource)
            provider.close()
            (dataSource as AutoCloseable).close()
        }
    }

    /** Persiste la publicación y la indexa con el proveedor real. */
    private fun indexPublication(
        dataSource: DataSource,
        provider: E5EmbeddingProvider,
        repository: FragmentRepositoryJdbc,
    ): Int {
        val publication = publication()
        PublicationRepositoryJdbc(dataSource).save(publication)
        return FragmentIndexer(provider, repository).index(publication)
    }

    /** La publicación produce dos fragmentos (uno por artículo). */
    private fun checkIndexedCount(count: Int) {
        assertEquals(2, count, "la publicación de prueba debe fragmentarse en dos artículos")
    }

    /** La consulta sobre ayudas devuelve primero el artículo relevante (orden 0). */
    private fun checkRelevantFragmentFirst(provider: E5EmbeddingProvider, repository: FragmentRepositoryJdbc) {
        val matches = HybridSearch(provider, repository).search(QUERY, limit = 10)

        assertEquals(2, matches.size)
        assertEquals(0, matches.first().fragment.order, "el artículo de ayudas debe ir primero")
        assertTrue(
            matches.first().distance < matches[1].distance,
            "el fragmento relevante debe distar menos: ${matches.first().distance} vs ${matches[1].distance}",
        )
        println("FragmentIndexerLiveTest: first=${matches.first().distance} second=${matches[1].distance}")
    }

    /** El filtro de categoría incluye la publicación y excluye una categoría distinta. */
    private fun checkMetadataFilter(provider: E5EmbeddingProvider, repository: FragmentRepositoryJdbc) {
        val search = HybridSearch(provider, repository)
        val included = search.search(QUERY, SearchFilter(category = CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS), limit = 10)
        val excluded = search.search(QUERY, SearchFilter(category = CategoriaDto.NORMAS_Y_LEGISLACION), limit = 10)

        assertEquals(2, included.size, "la categoría de la publicación debe incluirla")
        assertTrue(excluded.isEmpty(), "otra categoría no debe devolverla")
    }

    /** El embedding guardado se lee con 384 dimensiones (round-trip real). */
    private fun checkStoredEmbedding(repository: FragmentRepositoryJdbc) {
        val stored = assertNotNull(repository.findEmbedding(PUBLICATION_ID, 0))

        assertEquals(E5EmbeddingProvider.DIMENSIONS, stored.size)
    }

    /** Publicación de prueba con dos artículos (uno relevante para [QUERY]). */
    private fun publication(): Publicacion = Publicacion(
        id = PUBLICATION_ID,
        titulo = "Convocatoria de ayudas para prácticas formativas",
        fechaPublicacion = DATE,
        organismo = "Ministerio de Universidades",
        seccion = SeccionBoeDto.III,
        epigrafe = null,
        texto = RELEVANT_ARTICLE + "\n" + UNRELATED_ARTICLE,
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=$PUBLICATION_ID",
        urlXml = null,
        urlPdf = null,
        rango = null,
        categoria = CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS,
    )

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
        return fromEnv ?: defaultDir?.let { it.resolve(MODEL_FILE) to it.resolve(TOKENIZER_FILE) }
    }

    /** Borra la publicación de prueba (el `ON DELETE CASCADE` limpia sus fragmentos). */
    private fun deleteTestData(dataSource: DataSource) {
        dataSource.executeUpdate("DELETE FROM publicacion WHERE id LIKE ?") { statement ->
            statement.setString(1, "$PREFIX%")
        }
    }

    /**
     * Entorno del test: variables del proceso más, si falta alguna, las de la
     * `.env` de la raíz del producto (sin exportar).
     */
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
        const val DATE = "2026-10-09"
        const val PREFIX = "FT00017-LIVE-INDEXER"
        const val PUBLICATION_ID = "FT00017-LIVE-INDEXER"

        /** Rutas relativas posibles según el directorio de trabajo del test. */
        val DEFAULT_DIRS = listOf(
            Path.of("models/multilingual-e5-small"),
            Path.of("backend/models/multilingual-e5-small"),
        )

        /** Consulta del escenario: debe casar con el artículo de ayudas. */
        const val QUERY = "ayudas para prácticas formativas de estudiantes universitarios"

        /** Artículo relevante para [QUERY]. */
        const val RELEVANT_ARTICLE =
            "Artículo 1\nConvocatoria de ayudas para la realización de prácticas formativas en " +
                "empresas dirigida a estudiantes universitarios."

        /** Artículo sin relación con [QUERY]. */
        const val UNRELATED_ARTICLE =
            "Artículo 2\nReal Decreto por el que se modifica el régimen de infracciones y sanciones " +
                "en materia de tráfico, circulación de vehículos a motor y seguridad vial."
    }
}
