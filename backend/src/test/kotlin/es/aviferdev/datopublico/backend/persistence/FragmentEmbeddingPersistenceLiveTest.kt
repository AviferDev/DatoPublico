package es.aviferdev.datopublico.backend.persistence

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.model.SeccionBoeDto
import java.io.File
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Prueba **opt-in** contra el PostgreSQL real de `docker-compose` (escenarios 1–4
 * del spec): guardar embeddings sintéticos, recuperar el vecino más cercano,
 * excluir los fragmentos sin embedding y comprobar que el plan **usa** el índice
 * HNSW.
 *
 * Se salta por defecto (`DB_LIVE_TEST != 1`) para que el gate y la CI **no**
 * dependan de una base de datos. Requiere el esquema migrado
 * (`./gradlew :backend:flywayMigrate`) y una `.env` o el entorno con
 * `POSTGRES_*`. Para ejecutarla:
 *
 * ```
 * DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*FragmentEmbeddingPersistenceLiveTest'
 * ```
 */
class FragmentEmbeddingPersistenceLiveTest {

    @Test
    fun `saves embeddings, returns the nearest and uses the hnsw index`() {
        if (System.getenv("DB_LIVE_TEST") != "1") {
            println("FragmentEmbeddingPersistenceLiveTest omitido: exporta DB_LIVE_TEST=1 para el test real.")
        } else {
            val dataSource = Database.createDataSource(DatabaseConfig.fromEnv(environment()))
            try {
                dataSource.deletePublication(PUBLICATION_ID)
                val repository = prepare(dataSource)
                checkFailFast(repository)
                try {
                    checkSaveEmbeddingAndNearest(repository)
                    checkNullEmbeddingExcluded(repository)
                    checkHnswIndexUsed(dataSource)
                } finally {
                    dataSource.deletePublication(PUBLICATION_ID)
                }
            } finally {
                (dataSource as AutoCloseable).close()
            }
        }
    }

    /** Guarda la publicación y tres fragmentos (orden 1 sin embedding). */
    private fun prepare(dataSource: DataSource): FragmentRepositoryJdbc {
        PublicationRepositoryJdbc(dataSource).save(publication())
        val repository = FragmentRepositoryJdbc(dataSource)
        repository.save(fragment(order = 0, reference = "Artículo 1"))
        repository.save(fragment(order = 1, reference = "Artículo 2"))
        repository.save(fragment(order = 2, reference = "Artículo 3"))
        return repository
    }

    /** Escenario 4: dimensión y límite inválidos fallan sin tocar la base de datos. */
    private fun checkFailFast(repository: FragmentRepositoryJdbc) {
        assertFailsWith<IllegalArgumentException> { repository.saveEmbedding(PUBLICATION_ID, 0, floatArrayOf(1f)) }
        assertFailsWith<IllegalArgumentException> { repository.findNearest(axisVector(0), limit = 0) }
        assertFailsWith<IllegalArgumentException> { repository.findNearest(floatArrayOf(1f), limit = 1) }
    }

    /** Escenarios 1–3: el vecino más cercano es el propio vector; el nulo se excluye. */
    private fun checkSaveEmbeddingAndNearest(repository: FragmentRepositoryJdbc) {
        assertTrue(repository.saveEmbedding(PUBLICATION_ID, 0, axisVector(0)))
        assertTrue(repository.saveEmbedding(PUBLICATION_ID, 2, axisVector(1)))
        assertFalse(repository.saveEmbedding(PUBLICATION_ID, 9, axisVector(0)))

        val matches = repository.findNearest(axisVector(0), limit = 10)
        val first = assertNotNull(matches.firstOrNull())
        val second = assertNotNull(matches.getOrNull(1))

        assertEquals(0, first.fragment.order)
        assertEquals("Artículo 1", first.fragment.reference)
        assertEquals("Contenido 1", first.fragment.content)
        assertTrue(first.distance < 1e-4, "la distancia al propio vector debe ser ~0, fue ${first.distance}")
        assertTrue(first.distance < second.distance, "el vecino más cercano debe ir primero")
        println(
            "FragmentEmbeddingPersistenceLiveTest: nearest=${first.fragment.order} " +
                "distance=${first.distance} second=${second.fragment.order} distance=${second.distance}"
        )
    }

    /** Escenario 3: el fragmento sin embedding no aparece en la consulta. */
    private fun checkNullEmbeddingExcluded(repository: FragmentRepositoryJdbc) {
        val orders = repository.findNearest(axisVector(0), limit = 10).map { match -> match.fragment.order }

        assertFalse(orders.contains(1), "el fragmento sin embedding no debe devolverse: $orders")
        assertEquals(listOf(0, 2), orders)
    }

    /** Escenario 2: el índice HNSW existe y el plan lo usa con `enable_seqscan = off`. */
    private fun checkHnswIndexUsed(dataSource: DataSource) {
        val definitions = dataSource.queryRows(
            INDEX_LOOKUP,
            { },
            { rows -> rows.getString("indexdef") },
        )
        assertTrue(
            definitions.any { definition -> definition.contains("hnsw") && definition.contains("vector_cosine_ops") },
            "no se encontró el índice HNSW coseno: $definitions",
        )

        val plan = explainNearest(dataSource, PgVector.toLiteral(axisVector(0)))
        println("FragmentEmbeddingPersistenceLiveTest: plan=\n$plan")
        assertTrue(
            plan.contains("Index Scan using $INDEX_NAME"),
            "el plan no usa el índice HNSW:\n$plan",
        )
    }

    /** Plan de la consulta de vecinos con `enable_seqscan = off` (misma conexión). */
    private fun explainNearest(dataSource: DataSource, literal: String): String {
        val plan = mutableListOf<String>()
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("SET enable_seqscan = off")
            }
            connection.prepareStatement(EXPLAIN_NEAREST).use { statement ->
                statement.setString(1, literal)
                statement.setString(2, literal)
                statement.setInt(3, NEAREST_LIMIT)
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        plan.add(rows.getString("QUERY PLAN"))
                    }
                }
            }
        }
        return plan.joinToString("\n")
    }

    /** Vector unitario de 384 dims con `1` en [position] (distancia coseno bien definida). */
    private fun axisVector(position: Int): FloatArray =
        FloatArray(PgVector.DIMENSIONS).also { array -> array[position] = 1f }

    private fun fragment(order: Int, reference: String): FragmentEntity = FragmentEntity(
        publicationId = PUBLICATION_ID,
        order = order,
        reference = reference,
        content = "Contenido ${order + 1}",
    )

    private fun publication(): Publicacion = Publicacion(
        id = PUBLICATION_ID,
        titulo = "Resolución de prueba de indexado vectorial",
        fechaPublicacion = DATE,
        organismo = null,
        seccion = SeccionBoeDto.II_A,
        epigrafe = null,
        texto = null,
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=$PUBLICATION_ID",
        urlXml = null,
        urlPdf = null,
        rango = null,
    )

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

    private fun DataSource.deletePublication(id: String) {
        executeUpdate("DELETE FROM publicacion WHERE id = ?") { statement ->
            statement.setString(1, id)
        }
    }

    private companion object {
        const val DATE = "2026-10-09"
        const val PUBLICATION_ID = "FT00016-LIVE-EMBEDDINGS"
        const val INDEX_NAME = "fragmento_embedding_hnsw_idx"
        const val NEAREST_LIMIT = 10

        const val INDEX_LOOKUP =
            "SELECT indexdef FROM pg_indexes WHERE tablename = 'fragmento' AND indexname = '$INDEX_NAME'"

        const val EXPLAIN_NEAREST = """
            EXPLAIN SELECT id, publicacion_id, orden, referencia, contenido,
                    embedding <=> ?::vector AS distance
            FROM fragmento
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> ?::vector
            LIMIT ?
        """
    }
}
