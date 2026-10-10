package es.aviferdev.datopublico.backend.rag.retrieval

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.persistence.FragmentRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.PgVector
import es.aviferdev.datopublico.backend.persistence.PublicationRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.executeUpdate
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import java.io.File
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Prueba **opt-in** contra el PostgreSQL real de `docker-compose` (escenarios 1–4
 * del spec): recuperación híbrida con **vectores sintéticos** y filtros de
 * metadatos (categoría, rango de fechas inclusivo, sección + organismo), orden por
 * distancia, exclusión de fragmentos sin embedding y *round-trip* de
 * `findEmbedding`.
 *
 * Se salta por defecto (`DB_LIVE_TEST != 1`) para que el gate y la CI **no**
 * dependan de una base de datos. Requiere el esquema migrado
 * (`./gradlew :backend:flywayMigrate`) y una `.env` o el entorno con `POSTGRES_*`:
 *
 * ```
 * DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*HybridSearchLiveTest'
 * ```
 */
class HybridSearchLiveTest {

    @Test
    fun `filters by metadata and orders by distance`() {
        if (System.getenv("DB_LIVE_TEST") != "1") {
            println("HybridSearchLiveTest omitido: exporta DB_LIVE_TEST=1 para el test real.")
        } else {
            val dataSource = Database.createDataSource(DatabaseConfig.fromEnv(environment()))
            try {
                deleteTestData(dataSource)
                prepare(dataSource)
                val repository = FragmentRepositoryJdbc(dataSource)
                checkCategoryFilter(repository)
                checkDateRange(repository)
                checkEmptyFilter(repository)
                checkCombinedFilter(repository)
                checkNullEmbeddingExcluded(repository)
                checkFindEmbedding(repository)
                checkHybridSearch(repository)
            } finally {
                deleteTestData(dataSource)
                (dataSource as AutoCloseable).close()
            }
        }
    }

    /** Crea publicaciones y fragmentos con embeddings sintéticos (uno sin embedding). */
    private fun prepare(dataSource: DataSource) {
        val publications = PublicationRepositoryJdbc(dataSource)
        publications.save(publication(PUB_A, DATE_1, SeccionBoeDto.I, CategoriaDto.NORMAS_Y_LEGISLACION, ORG_A))
        publications.save(publication(PUB_B, DATE_2, SeccionBoeDto.II_B, CategoriaDto.OPOSICIONES_Y_EMPLEO_PUBLICO, ORG_B))
        publications.save(publication(PUB_C, DATE_3, SeccionBoeDto.III, CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS, ORG_A))
        publications.save(publication(PUB_NULL, DATE_1, SeccionBoeDto.I, CategoriaDto.NORMAS_Y_LEGISLACION, ORG_A))
        val fragments = FragmentRepositoryJdbc(dataSource)
        saveFragment(fragments, PUB_A, axis = 0)
        saveFragment(fragments, PUB_B, axis = 1)
        saveFragment(fragments, PUB_C, axis = 2)
        fragments.save(fragmentEntity(PUB_NULL))
    }

    /** Escenario 1: el filtro de categoría devuelve solo la publicación de esa categoría. */
    private fun checkCategoryFilter(repository: FragmentRepositoryJdbc) {
        val filter = SearchFilter(category = CategoriaDto.NORMAS_Y_LEGISLACION)

        val matches = repository.findNearest(axisVector(0), filter, limit = 10)

        assertEquals(listOf(PUB_A), matches.map { match -> match.fragment.publicationId })
        assertTrue(matches.first().distance < 1e-4, "el propio vector debe distar ~0: ${matches.first().distance}")
    }

    /** Escenario 2: el rango de fechas es inclusivo. */
    private fun checkDateRange(repository: FragmentRepositoryJdbc) {
        val filter = SearchFilter(publishedFrom = DATE_2, publishedTo = DATE_2)

        val matches = repository.findNearest(axisVector(0), filter, limit = 10)

        assertEquals(listOf(PUB_B), matches.map { match -> match.fragment.publicationId })
    }

    /** Escenario 3: sin filtro equivale a la similitud pura de FT00016. */
    private fun checkEmptyFilter(repository: FragmentRepositoryJdbc) {
        val matches = repository.findNearest(axisVector(0), SearchFilter(), limit = 10)
        val ids = matches.map { match -> match.fragment.publicationId }

        assertEquals(setOf(PUB_A, PUB_B, PUB_C), ids.toSet(), "solo los fragmentos con embedding")
        assertEquals(PUB_A, ids.first(), "el vecino más cercano va primero")
    }

    /** Escenario 4: varios filtros se combinan con AND. */
    private fun checkCombinedFilter(repository: FragmentRepositoryJdbc) {
        val filter = SearchFilter(
            publishedFrom = DATE_3,
            section = SeccionBoeDto.III,
            organization = ORG_A,
        )

        val matches = repository.findNearest(axisVector(0), filter, limit = 10)

        assertEquals(listOf(PUB_C), matches.map { match -> match.fragment.publicationId })
    }

    /** Escenario 1: el fragmento sin embedding no aparece. */
    private fun checkNullEmbeddingExcluded(repository: FragmentRepositoryJdbc) {
        val ids = repository.findNearest(axisVector(0), SearchFilter(), limit = 10)
            .map { match -> match.fragment.publicationId }

        assertTrue(!ids.contains(PUB_NULL), "el fragmento sin embedding no debe devolverse: $ids")
    }

    /** `findEmbedding` cierra el *round-trip* real del vector y devuelve `null` si no hay. */
    private fun checkFindEmbedding(repository: FragmentRepositoryJdbc) {
        val stored = assertNotNull(repository.findEmbedding(PUB_A, 0))

        assertTrue(stored.contentEquals(axisVector(0)), "el vector debe conservarse en el round-trip")
        assertNull(repository.findEmbedding(PUB_NULL, 0), "un fragmento sin embedding devuelve null")
    }

    /** `HybridSearch` compone el proveedor (doble) y el repositorio con filtro. */
    private fun checkHybridSearch(repository: FragmentRepositoryJdbc) {
        val provider = FixedQueryEmbeddingProvider(axisVector(0))
        val search = HybridSearch(provider, repository)

        val matches = search.search("consulta de prueba", SearchFilter(section = SeccionBoeDto.I), limit = 10)

        assertEquals(listOf(PUB_A), matches.map { match -> match.fragment.publicationId })
    }

    /** Guarda un fragmento y su embedding sintético en el eje [axis]. */
    private fun saveFragment(repository: FragmentRepositoryJdbc, publicationId: String, axis: Int) {
        repository.save(fragmentEntity(publicationId))
        repository.saveEmbedding(publicationId, 0, axisVector(axis))
    }

    /** Fragmento de prueba (orden 0) de la publicación [publicationId]. */
    private fun fragmentEntity(publicationId: String): FragmentEntity = FragmentEntity(
        publicationId = publicationId,
        order = 0,
        reference = "Artículo 1",
        content = "Contenido de $publicationId",
    )

    /** Publicación de prueba con sus metadatos de filtrado. */
    private fun publication(
        id: String,
        date: String,
        section: SeccionBoeDto,
        category: CategoriaDto,
        organization: String,
    ): Publicacion = Publicacion(
        id = id,
        titulo = "Publicación de prueba $id",
        fechaPublicacion = date,
        organismo = organization,
        seccion = section,
        epigrafe = null,
        texto = null,
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=$id",
        urlXml = null,
        urlPdf = null,
        rango = null,
        categoria = category,
    )

    /** Vector unitario de 384 dims con `1` en [position] (distancia coseno bien definida). */
    private fun axisVector(position: Int): FloatArray =
        FloatArray(PgVector.DIMENSIONS).also { array -> array[position] = 1f }

    /** Borra las publicaciones de prueba (el `ON DELETE CASCADE` limpia sus fragmentos). */
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

    /** Doble de [EmbeddingProvider] que devuelve un vector fijo (sin cargar el modelo E5). */
    private class FixedQueryEmbeddingProvider(private val vector: FloatArray) : EmbeddingProvider {
        override val dimensions: Int = vector.size

        override fun embedQuery(text: String): FloatArray = vector

        override fun embedPassage(text: String): FloatArray = vector

        override fun close() = Unit
    }

    private companion object {
        const val PREFIX = "FT00017-LIVE-"
        const val PUB_A = "FT00017-LIVE-A"
        const val PUB_B = "FT00017-LIVE-B"
        const val PUB_C = "FT00017-LIVE-C"
        const val PUB_NULL = "FT00017-LIVE-NULL"
        const val DATE_1 = "2026-10-01"
        const val DATE_2 = "2026-10-15"
        const val DATE_3 = "2026-11-01"
        const val ORG_A = "Ministerio A"
        const val ORG_B = "Ministerio B"
    }
}
