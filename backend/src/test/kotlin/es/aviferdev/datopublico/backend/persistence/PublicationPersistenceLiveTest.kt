package es.aviferdev.datopublico.backend.persistence

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.PlazoDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import java.io.File
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Prueba **opt-in** contra el PostgreSQL real de `docker-compose` (escenarios
 * 1–5 del spec).
 *
 * Se salta por defecto (`DB_LIVE_TEST != 1`) para que el gate y la CI **no**
 * dependan de una base de datos. Requiere el esquema migrado
 * (`./gradlew :backend:flywayMigrate`) y una `.env` o el entorno con
 * `POSTGRES_*`. Para ejecutarla:
 *
 * ```
 * DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*PublicationPersistenceLiveTest'
 * ```
 */
class PublicationPersistenceLiveTest {

    @Test
    fun `saves and retrieves publications, upsert, nulls and cascade`() {
        if (System.getenv("DB_LIVE_TEST") != "1") {
            println("PublicationPersistenceLiveTest omitido: exporta DB_LIVE_TEST=1 para el test real.")
        } else {
            val dataSource = Database.createDataSource(DatabaseConfig.fromEnv(environment()))
            try {
                checkTables(dataSource)
                checkSaveAndRetrieve(dataSource)
                checkUpsert(dataSource)
                checkNulls(dataSource)
                checkCascade(dataSource)
            } finally {
                (dataSource as AutoCloseable).close()
            }
        }
    }

    private fun checkTables(dataSource: DataSource) {
        val tables = dataSource.queryRows(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            { },
            { rows -> rows.getString("table_name") },
        )
        assertTrue(
            tables.containsAll(listOf("publicacion", "fragmento", "resumen", "cola_revision")),
            "faltan tablas de dominio: $tables",
        )
    }

    private fun checkSaveAndRetrieve(dataSource: DataSource) {
        val repository = PublicationRepositoryJdbc(dataSource)
        val publication = fullPublication(ID_COMPLETE)
        try {
            repository.save(publication)

            val retrieved = repository.findById(ID_COMPLETE)
            assertEquals(publication, retrieved)
            assertEquals(1, repository.listByDate(DATE).size)
        } finally {
            dataSource.deletePublication(ID_COMPLETE)
        }
    }

    private fun checkUpsert(dataSource: DataSource) {
        val repository = PublicationRepositoryJdbc(dataSource)
        try {
            repository.save(fullPublication(ID_UPSERT))
            val before = dataSource.updatedAt(ID_UPSERT)
            Thread.sleep(10)
            repository.save(fullPublication(ID_UPSERT).copy(titulo = "Título actualizado"))

            assertEquals(1, dataSource.countPublications(ID_UPSERT))
            assertEquals("Título actualizado", repository.findById(ID_UPSERT)?.titulo)
            assertTrue(dataSource.updatedAt(ID_UPSERT).isAfter(before))
        } finally {
            dataSource.deletePublication(ID_UPSERT)
        }
    }

    private fun checkNulls(dataSource: DataSource) {
        val repository = PublicationRepositoryJdbc(dataSource)
        val publication = publicationWithNulls(ID_NULLS)
        try {
            repository.save(publication)

            assertEquals(publication, repository.findById(ID_NULLS))
        } finally {
            dataSource.deletePublication(ID_NULLS)
        }
    }

    private fun checkCascade(dataSource: DataSource) {
        val publications = PublicationRepositoryJdbc(dataSource)
        val fragments = FragmentRepositoryJdbc(dataSource)
        val summaries = SummaryRepositoryJdbc(dataSource)
        val queue = ReviewQueueRepositoryJdbc(dataSource)
        try {
            publications.save(publicationWithNulls(ID_CASCADE))
            val fragment = fragments.save(
                FragmentEntity(
                    publicationId = ID_CASCADE,
                    order = 1,
                    reference = "Artículo 1",
                    content = "Contenido del fragmento",
                )
            )
            val summary = summaries.save(
                SummaryEntity(
                    publicationId = ID_CASCADE,
                    whatChanges = "Cambia algo",
                    whomItAffects = "Afecta a alguien",
                    keyFigures = listOf("1", "2"),
                    officialSource = "https://www.boe.es/diario_boe/txt.php?id=$ID_CASCADE",
                )
            )
            val summaryId = requireNotNull(summary.id) { "el resumen no recibió id" }
            val entry = queue.save(ReviewQueueEntity(summaryId = summaryId, reason = "Confianza baja"))

            assertNotNull(fragment.id)
            assertNotNull(entry.id)
            assertEquals(1, fragments.listByPublication(ID_CASCADE).size)
            assertEquals(listOf("1", "2"), summaries.findByPublication(ID_CASCADE)?.keyFigures)
            assertEquals(1, queue.listBySummary(summaryId).size)

            dataSource.deletePublication(ID_CASCADE)

            assertEquals(0, fragments.listByPublication(ID_CASCADE).size)
            assertNull(summaries.findByPublication(ID_CASCADE))
            assertEquals(0, queue.listBySummary(summaryId).size)
        } finally {
            dataSource.deletePublication(ID_CASCADE)
        }
    }

    private fun fullPublication(id: String): Publicacion = Publicacion(
        id = id,
        titulo = "Resolución de 8 de octubre de 2026",
        fechaPublicacion = DATE,
        organismo = "MINISTERIO DE EDUCACIÓN",
        seccion = SeccionBoeDto.II_A,
        epigrafe = "Becas y subvenciones",
        texto = "Primero. Convocar...\nSegundo. El plazo...",
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=$id",
        urlXml = "https://www.boe.es/diario_boe/xml.php?id=$id",
        urlPdf = "https://www.boe.es/boe/dias/2026/10/09/pdfs/A00001-00002.pdf",
        rango = "Resolución",
        categoria = CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS,
        plazo = PlazoDto(fechaLimite = "2026-11-30", descripcion = "Diez días hábiles"),
    )

    private fun publicationWithNulls(id: String): Publicacion = Publicacion(
        id = id,
        titulo = "Resolución sin metadatos enriquecidos",
        fechaPublicacion = DATE,
        organismo = null,
        seccion = SeccionBoeDto.II_A,
        epigrafe = null,
        texto = null,
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=$id",
        urlXml = null,
        urlPdf = null,
        rango = null,
    )

    /**
     * Entorno del test: variables del proceso más, si falta alguna, las de la
     * `.env` de la raíz del producto (sin exportar). Así el comando opt-in
     * funciona con solo `docker compose up -d` y `.env`.
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

    private fun DataSource.countPublications(id: String): Long =
        queryRows(
            "SELECT count(*) AS total FROM publicacion WHERE id = ?",
            { statement -> statement.setString(1, id) },
            { rows -> rows.getLong("total") },
        ).first()

    private fun DataSource.updatedAt(id: String): Instant =
        queryRows(
            "SELECT actualizado_en FROM publicacion WHERE id = ?",
            { statement -> statement.setString(1, id) },
            { rows -> rows.toInstant("actualizado_en") },
        ).first()

    private fun ResultSet.toInstant(column: String): Instant =
        getObject(column, OffsetDateTime::class.java).toInstant()

    private companion object {
        const val DATE = "2026-10-09"
        const val ID_COMPLETE = "FT00008-LIVE-COMPLETA"
        const val ID_UPSERT = "FT00008-LIVE-UPSERT"
        const val ID_NULLS = "FT00008-LIVE-NULOS"
        const val ID_CASCADE = "FT00008-LIVE-CASCADA"
    }
}
