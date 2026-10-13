package es.aviferdev.datopublico.backend.persistence

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.backend.rag.review.SummaryState
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import java.io.File
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prueba **opt-in** contra el PostgreSQL real de `docker-compose` (escenario 3 del
 * spec): `FeedRepositoryJdbc.listPublishedByIds` devuelve **solo** las
 * publicaciones del lote con resumen `estado = 'publicado'`; la que está
 * `en_cola_revision` **no** se sirve, y un lote vacío no consulta la base.
 *
 * Se salta por defecto (`DB_LIVE_TEST != 1`) para que el gate y la CI **no**
 * dependan de una base de datos. Requiere el esquema migrado (`V1`…`V5`,
 * `./gradlew :backend:flywayMigrate`) y una `.env` o el entorno con `POSTGRES_*`:
 *
 * ```
 * DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*SearchPersistenceLiveTest'
 * ```
 */
class SearchPersistenceLiveTest {

    @Test
    fun `only the publications with a published summary are returned for the batch`() {
        if (System.getenv("DB_LIVE_TEST") != "1") {
            println("SearchPersistenceLiveTest omitido: exporta DB_LIVE_TEST=1 para el test real.")
        } else {
            val dataSource = Database.createDataSource(DatabaseConfig.fromEnv(environment()))
            try {
                clean(dataSource)
                try {
                    runLive(dataSource)
                } finally {
                    clean(dataSource)
                }
            } finally {
                (dataSource as AutoCloseable).close()
            }
        }
    }

    /** Persiste un resumen publicado y otro en revisión y comprueba el filtro por lote. */
    private fun runLive(dataSource: DataSource) {
        val publications = PublicationRepositoryJdbc(dataSource)
        publications.save(publication(PUBLISHED_ID))
        publications.save(publication(QUEUED_ID))
        val summaries = SummaryRepositoryJdbc(dataSource)
        summaries.save(summary(PUBLISHED_ID, SummaryState.PUBLISHED.persistedValue))
        summaries.save(summary(QUEUED_ID, SummaryState.IN_REVIEW.persistedValue))

        val repository = FeedRepositoryJdbc(dataSource)
        val items = repository.listPublishedByIds(listOf(PUBLISHED_ID, QUEUED_ID))
        val empty = repository.listPublishedByIds(emptyList())

        assertEquals(1, items.size)
        assertEquals(PUBLISHED_ID, items.first().publication.id)
        assertEquals(SummaryState.PUBLISHED.persistedValue, items.first().summary.status)
        assertTrue(empty.isEmpty(), "un lote vacío no abre conexión ni devuelve nada")
        println("SearchPersistenceLiveTest: solo el resumen publicado del lote se sirve OK")
    }

    private fun publication(id: String): Publicacion = Publicacion(
        id = id,
        titulo = "Publicación de prueba de la búsqueda",
        fechaPublicacion = DATE,
        organismo = null,
        seccion = SeccionBoeDto.II_A,
        epigrafe = null,
        texto = null,
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=$id",
        urlXml = null,
        urlPdf = null,
        rango = null,
        categoria = CategoriaDto.INFORMACION_PUBLICA_Y_CONCESIONES,
    )

    private fun summary(publicationId: String, status: String): SummaryEntity = SummaryEntity(
        publicationId = publicationId,
        whatChanges = "Cambia algo en $publicationId",
        whomItAffects = "A la ciudadanía",
        keyFigures = emptyList(),
        officialSource = "https://www.boe.es/diario_boe/txt.php?id=$publicationId",
        aiNotice = true,
        status = status,
    )

    /** Borra las publicaciones de prueba (la cascada limpia `resumen`). */
    private fun clean(dataSource: DataSource) {
        dataSource.executeUpdate("DELETE FROM publicacion WHERE id IN (?, ?)") { statement ->
            statement.setString(1, PUBLISHED_ID)
            statement.setString(2, QUEUED_ID)
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
        const val DATE = "2026-10-11"
        const val PUBLISHED_ID = "FT00028-LIVE-PUBLISHED"
        const val QUEUED_ID = "FT00028-LIVE-QUEUED"
    }
}
