package es.aviferdev.datopublico.backend.search

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.backend.persistence.FeedRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.FragmentRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.PublicationRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.SummaryEntity
import es.aviferdev.datopublico.backend.persistence.SummaryRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.executeUpdate
import es.aviferdev.datopublico.backend.rag.embeddings.E5EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingConfig
import es.aviferdev.datopublico.backend.rag.indexing.FragmentIndexer
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.BoeSectionDto
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Prueba **opt-in** de extremo a extremo (escenario 8 del spec): indexa una
 * publicación con el **modelo E5 real** y busca con [SearchService],
 * comprobando que la publicación relevante aparece primero, que solo se sirve con
 * su resumen publicado y que los filtros de categoría y rango de fechas se
 * respetan.
 *
 * Requiere **las dos** variables: `DB_LIVE_TEST=1` (PostgreSQL migrado de
 * `docker-compose`) y `EMBEDDING_LIVE_TEST=1` (modelo E5 descargado en
 * `backend/models/`). Sin ellas se omite sin abrir conexión ni cargar el modelo:
 *
 * ```
 * backend/tools/download-embedding-model.sh
 * cp .env.example .env && docker compose up -d
 * ./gradlew :backend:flywayMigrate
 * DB_LIVE_TEST=1 EMBEDDING_LIVE_TEST=1 ./gradlew :backend:test --tests '*SearchServiceLiveTest'
 * ```
 */
class SearchServiceLiveTest {

    @Test
    fun `searches the indexed corpus with the real model and respects the filters`() {
        val artifacts = resolveArtifacts()
        if (System.getenv("DB_LIVE_TEST") != "1" || System.getenv("EMBEDDING_LIVE_TEST") != "1" || artifacts == null) {
            println(
                "SearchServiceLiveTest omitido: descarga el modelo con " +
                    "backend/tools/download-embedding-model.sh y exporta DB_LIVE_TEST=1 y EMBEDDING_LIVE_TEST=1."
            )
        } else {
            runEndToEnd(artifacts)
        }
    }

    /** Indexa con el modelo real y busca contra PostgreSQL, con limpieza final. */
    private fun runEndToEnd(artifacts: Pair<Path, Path>) {
        val dataSource = Database.createDataSource(DatabaseConfig.fromEnv(environment()))
        val provider = E5EmbeddingProvider.from(artifacts.first, artifacts.second)
        try {
            deleteTestData(dataSource)
            indexPublication(dataSource, provider)
            val service = service(dataSource, provider)
            checkRelevantFirst(service)
            checkCategoryFilter(service)
            checkDateRange(service)
        } finally {
            deleteTestData(dataSource)
            provider.close()
            (dataSource as AutoCloseable).close()
        }
    }

    /** Persiste la publicación con resumen publicado y la indexa con el proveedor real. */
    private fun indexPublication(dataSource: DataSource, provider: E5EmbeddingProvider) {
        PublicationRepositoryJdbc(dataSource).save(publication())
        SummaryRepositoryJdbc(dataSource).save(summary())
        FragmentIndexer(provider, FragmentRepositoryJdbc(dataSource)).index(publication())
    }

    /** La publicación relevante va primero y llega con su resumen publicado. */
    private fun checkRelevantFirst(service: SearchService) {
        val result = runBlocking { service.search(QUERY) }

        assertEquals(listOf(PUBLICATION_ID), result.results.map { publication -> publication.id })
        assertTrue(result.results.first().summary != null, "el resumen publicado debe viajar")
        println("SearchServiceLiveTest: publicación relevante servida OK")
    }

    /** El filtro de categoría incluye la publicación y excluye una categoría distinta. */
    private fun checkCategoryFilter(service: SearchService) {
        val included = runBlocking {
            service.search(QUERY, category = CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID)
        }
        val excluded = runBlocking {
            service.search(QUERY, category = CategoryDto.NORMS_AND_LEGISLATION)
        }

        assertEquals(1, included.results.size, "la categoría de la publicación debe incluirla")
        assertTrue(excluded.results.isEmpty(), "otra categoría no debe devolverla")
    }

    /** El rango de fechas incluye el día de la publicación. */
    private fun checkDateRange(service: SearchService) {
        val result = runBlocking {
            service.search(QUERY, publishedFrom = DATE, publishedTo = DATE)
        }

        assertEquals(listOf(PUBLICATION_ID), result.results.map { publication -> publication.id })
    }

    /** Servicio de búsqueda sobre el pool compartido y el proveedor real. */
    private fun service(dataSource: DataSource, provider: E5EmbeddingProvider): SearchService =
        SearchService(
            hybridSearch = HybridSearch(provider, FragmentRepositoryJdbc(dataSource)),
            feedRepository = FeedRepositoryJdbc(dataSource),
        )

    /** Publicación de prueba con dos artículos (uno relevante para [QUERY]). */
    private fun publication(): Publicacion = Publicacion(
        id = PUBLICATION_ID,
        title = "Convocatoria de ayudas para prácticas formativas",
        publicationDate = DATE,
        issuingBody = "Ministerio de Universidades",
        section = BoeSectionDto.III,
        heading = null,
        texto = RELEVANT_ARTICLE + "\n" + UNRELATED_ARTICLE,
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=$PUBLICATION_ID",
        urlXml = null,
        urlPdf = null,
        rango = null,
        category = CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID,
    )

    /** Resumen ciudadano publicado de la publicación de prueba. */
    private fun summary(): SummaryEntity = SummaryEntity(
        publicationId = PUBLICATION_ID,
        whatChanges = "Se convocan ayudas para prácticas formativas",
        whomItAffects = "Estudiantes universitarios",
        officialSource = "https://www.boe.es/diario_boe/txt.php?id=$PUBLICATION_ID",
        status = "publicado",
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

    /** Borra la publicación de prueba (el `ON DELETE CASCADE` limpia fragmentos y resumen). */
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
        const val PREFIX = "FT00028-LIVE-SEARCH"
        const val PUBLICATION_ID = "FT00028-LIVE-SEARCH"

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
