package es.aviferdev.datopublico.backend.persistence

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * Test **sin base de datos** del contrato SQL de la migración V5 (gate).
 *
 * Lee el fichero de migración del classpath y comprueba que crea el **índice único
 * parcial** de entradas pendientes por resumen (idempotencia del encolado de
 * FT00023) y que **no** edita migraciones anteriores ni toca la tabla `resumen`.
 * No ejecuta SQL.
 */
class ReviewQueueMigrationSqlTest {

    @Test
    fun `creates the partial unique index for pending reviews`() {
        val sql = migrationV5()

        assertContains(sql, "CREATE UNIQUE INDEX cola_revision_pendiente_unique")
        assertContains(sql, "ON cola_revision (resumen_id)")
        assertContains(sql, "WHERE estado = 'pendiente'")
    }

    @Test
    fun `does not edit earlier migrations nor touch the summary table`() {
        val sql = sqlWithoutComments(migrationV5()).lowercase()

        assertFalse(sql.contains("create table"), "no crea tablas nuevas")
        assertFalse(sql.contains("alter table"), "no altera tablas existentes")
        assertFalse(sql.contains("resumen ("), "no toca la tabla resumen")
        assertFalse(sql.contains("embedding"), "el embedding es de FT00016/V3")
    }

    private fun migrationV5(): String {
        val stream = checkNotNull(
            javaClass.getResourceAsStream("/db/migration/V5__cola_revision_pendiente_unique.sql")
        ) { "No se encontró la migración V5 en el classpath de test" }
        return stream.bufferedReader().use { reader -> reader.readText() }
    }

    /** Elimina los comentarios `--` para comprobar solo el SQL ejecutable. */
    private fun sqlWithoutComments(sql: String): String =
        sql.lineSequence()
            .map { line -> line.substringBefore("--") }
            .joinToString("\n")
}
