package es.aviferdev.datopublico.backend.persistence

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * Test **sin base de datos** del contrato SQL de la migración V3 (gate).
 *
 * Lee el fichero de migración del classpath y comprueba que añade la columna
 * `embedding vector(384)` (nullable) con su índice HNSW coseno, y que **no**
 * anticipa lo diferido (filtros de metadatos de la recuperación híbrida,
 * FT00017). No ejecuta SQL.
 */
class FragmentEmbeddingMigrationSqlTest {

    @Test
    fun `adds the nullable embedding column of 384 dimensions`() {
        val sql = migrationV3()

        assertContains(sql, "ALTER TABLE fragmento ADD COLUMN embedding vector(384)")
        assertFalse(sqlWithoutComments(sql).lowercase().contains("not null"), "la columna es nullable")
    }

    @Test
    fun `creates the hnsw index with cosine distance`() {
        val sql = migrationV3()

        assertContains(sql, "CREATE INDEX fragmento_embedding_hnsw_idx")
        assertContains(sql, "USING hnsw (embedding vector_cosine_ops)")
        assertContains(sql, "m = 16")
        assertContains(sql, "ef_construction = 64")
    }

    @Test
    fun `does not anticipate the FT00017 hybrid filters`() {
        val sql = sqlWithoutComments(migrationV3()).lowercase()

        assertFalse(sql.contains("select"), "la migración no debe contener consultas")
        assertFalse(sql.contains("where"), "sin filtros de metadatos (FT00017)")
        assertFalse(sql.contains("categoria"), "categoria es un filtro de FT00017")
        assertFalse(sql.contains("fecha_publicacion"), "la fecha es un filtro de FT00017")
    }

    private fun migrationV3(): String {
        val stream = checkNotNull(
            javaClass.getResourceAsStream("/db/migration/V3__fragmento_embedding.sql")
        ) { "No se encontró la migración V3 en el classpath de test" }
        return stream.bufferedReader().use { reader -> reader.readText() }
    }

    /** Elimina los comentarios `--` para comprobar solo el SQL ejecutable. */
    private fun sqlWithoutComments(sql: String): String =
        sql.lineSequence()
            .map { line -> line.substringBefore("--") }
            .joinToString("\n")
}
