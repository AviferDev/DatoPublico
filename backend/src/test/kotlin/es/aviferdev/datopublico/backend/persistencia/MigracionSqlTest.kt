package es.aviferdev.datopublico.backend.persistencia

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test **sin base de datos** del contrato SQL de la migración V2 (gate).
 *
 * Lee el fichero de migración del classpath y comprueba que declara las cuatro
 * tablas de dominio con sus FK en cascada, y que **no** anticipa lo diferido
 * (columna de embedding o índice HNSW, de FT00016). No ejecuta SQL.
 */
class MigracionSqlTest {

    @Test
    fun `crea las cuatro tablas de dominio`() {
        val sql = migracionV2()

        assertContains(sql, "CREATE TABLE publicacion")
        assertContains(sql, "CREATE TABLE fragmento")
        assertContains(sql, "CREATE TABLE resumen")
        assertContains(sql, "CREATE TABLE cola_revision")
    }

    @Test
    fun `declara las claves foraneas en cascada`() {
        val sql = migracionV2()

        assertContains(sql, "REFERENCES publicacion (id) ON DELETE CASCADE")
        assertContains(sql, "REFERENCES resumen (id) ON DELETE CASCADE")
        assertContains(sql, "UNIQUE (publicacion_id, orden)")
        assertContains(sql, "publicacion_id text NOT NULL UNIQUE")
    }

    @Test
    fun `no anticipa el embedding ni el indice HNSW de FT00016`() {
        val sql = sqlSinComentarios(migracionV2()).lowercase()

        assertFalse(sql.contains("embedding"), "la tabla fragmento no debe tener embedding aún")
        assertFalse(sql.contains("vector("), "el tipo vector es de FT00016")
        assertFalse(sql.contains("hnsw"), "el índice HNSW es de FT00016")
    }

    @Test
    fun `la fecha y el titulo de publicacion son obligatorios`() {
        val sql = migracionV2()

        assertContains(sql, "titulo             text NOT NULL")
        assertContains(sql, "fecha_publicacion  date NOT NULL")
        assertContains(sql, "url_oficial        text NOT NULL")
    }

    private fun migracionV2(): String {
        val stream = checkNotNull(
            javaClass.getResourceAsStream("/db/migration/V2__persistencia_publicaciones.sql")
        ) { "No se encontró la migración V2 en el classpath de test" }
        return stream.bufferedReader().use { lector -> lector.readText() }
    }

    /** Elimina los comentarios `--` para comprobar solo el SQL ejecutable. */
    private fun sqlSinComentarios(sql: String): String =
        sql.lineSequence()
            .map { linea -> linea.substringBefore("--") }
            .joinToString("\n")
}
