package es.aviferdev.datopublico.backend.persistence

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * Test **sin base de datos** del contrato SQL de la migración V4 (gate).
 *
 * Lee el fichero de migración del classpath y comprueba que crea las tablas
 * `resumen_cache` y `generacion_uso` con sus columnas e índices, que la caché
 * referencia a `publicacion` en cascada, que el log de uso **no** tiene FK y que
 * **no** anticipa lo diferido. No ejecuta SQL.
 */
class SummaryCacheMigrationSqlTest {

    @Test
    fun `creates the cache and usage tables`() {
        val sql = migrationV4()

        assertContains(sql, "CREATE TABLE resumen_cache")
        assertContains(sql, "CREATE TABLE generacion_uso")
    }

    @Test
    fun `declares the cache key as primary key and the publication foreign key`() {
        val sql = migrationV4()

        assertContains(sql, "cache_key      text PRIMARY KEY")
        assertContains(sql, "REFERENCES publicacion (id) ON DELETE CASCADE")
        assertContains(sql, "CREATE INDEX resumen_cache_publicacion_id_idx")
    }

    @Test
    fun `declares the token columns with defaults and the cache hit flag`() {
        val sql = migrationV4()

        assertContains(sql, "prompt_tokens     integer NOT NULL DEFAULT 0")
        assertContains(sql, "completion_tokens integer NOT NULL DEFAULT 0")
        assertContains(sql, "total_tokens      integer NOT NULL DEFAULT 0")
        assertContains(sql, "cache_hit         boolean NOT NULL DEFAULT false")
        assertContains(sql, "CREATE INDEX generacion_uso_publicacion_id_idx")
    }

    @Test
    fun `the usage log has no foreign key to the publication`() {
        val sql = sqlWithoutComments(migrationV4())

        val usageTable = sql.substringAfter("CREATE TABLE generacion_uso")
        assertFalse(
            usageTable.contains("REFERENCES"),
            "el log de uso no debe tener FK (no impide borrar una publicación)",
        )
    }

    @Test
    fun `does not edit earlier migrations nor anticipate the deferred features`() {
        val sql = sqlWithoutComments(migrationV4()).lowercase()

        assertFalse(sql.contains("embedding"), "el embedding es de FT00016/V3")
        assertFalse(sql.contains("hnsw"), "el índice HNSW es de FT00016/V3")
        assertFalse(sql.contains("resumen (") || sql.contains("alter table resumen"), "no toca la tabla resumen")
    }

    private fun migrationV4(): String {
        val stream = checkNotNull(
            javaClass.getResourceAsStream("/db/migration/V4__resumen_cache_uso.sql")
        ) { "No se encontró la migración V4 en el classpath de test" }
        return stream.bufferedReader().use { reader -> reader.readText() }
    }

    /** Elimina los comentarios `--` para comprobar solo el SQL ejecutable. */
    private fun sqlWithoutComments(sql: String): String =
        sql.lineSequence()
            .map { line -> line.substringBefore("--") }
            .joinToString("\n")
}
