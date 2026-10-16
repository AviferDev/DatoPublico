package es.aviferdev.datopublico.backend.ingestion.job

import es.aviferdev.datopublico.backend.ingestion.publication.BoePublicationParser
import es.aviferdev.datopublico.backend.ingestion.publication.Publication
import es.aviferdev.datopublico.backend.ingestion.summary.BoeSummaryClient
import es.aviferdev.datopublico.backend.ingestion.summary.SummaryEntry
import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.backend.persistence.PublicationRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.executeUpdate
import es.aviferdev.datopublico.backend.persistence.queryRows
import es.aviferdev.datopublico.model.BoeSectionDto
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Prueba **opt-in** del job contra el PostgreSQL real de `docker-compose`
 * (escenario 6 del spec): el sumario y el parser son de prueba, el repositorio es
 * el JDBC real. Reejecutar la misma fecha deja una fila por `id` y refresca
 * `actualizado_en` (idempotencia real del *upsert*).
 *
 * Se salta por defecto (`DB_LIVE_TEST != 1`) para que el gate y la CI **no**
 * dependan de una base de datos. Requiere el esquema migrado
 * (`./gradlew :backend:flywayMigrate`) y una `.env` o el entorno con
 * `POSTGRES_*`:
 *
 * ```
 * DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*IngestionJobLiveTest'
 * ```
 */
class IngestionJobLiveTest {

    @Test
    fun `running twice for the same date does not duplicate and refreshes updated_at`() = runTest {
        if (System.getenv("DB_LIVE_TEST") != "1") {
            println("IngestionJobLiveTest omitido: exporta DB_LIVE_TEST=1 para el test real.")
        } else {
            val dataSource = Database.createDataSource(DatabaseConfig.fromEnv(environment()))
            try {
                dataSource.clean()
                val job = DailyIngestionJob(
                    summaryClient = testSummary(),
                    publicationParser = testParser(),
                    repository = PublicationRepositoryJdbc(dataSource),
                )

                val first = job.run(DATE)
                val before = dataSource.updatedAt(ID_1)
                Thread.sleep(10)
                val second = job.run(DATE)

                assertEquals(2, first.saved, "la primera pasada guarda las dos entradas")
                assertEquals(2, second.saved, "la segunda pasada reaplica el upsert")
                assertEquals(2L, dataSource.count(), "una fila por id, sin duplicados")
                assertTrue(
                    dataSource.updatedAt(ID_1).isAfter(before),
                    "actualizado_en debe avanzar en la segunda pasada",
                )
            } finally {
                dataSource.clean()
                (dataSource as AutoCloseable).close()
            }
        }
    }

    /** Sumario de prueba con dos entradas de la sección I. */
    private fun testSummary(): BoeSummaryClient = object : BoeSummaryClient {
        override suspend fun fetchSummary(date: LocalDate): List<SummaryEntry> =
            listOf(entry(ID_1), entry(ID_2))
    }

    /** Parser de prueba: proyecta cada entrada a una [Publication] sin red. */
    private fun testParser(): BoePublicationParser = object : BoePublicationParser {
        override suspend fun parse(entry: SummaryEntry): Publication = Publication(
            id = entry.identifier,
            title = entry.title,
            publicationDate = entry.publicationDate,
            issuingBody = entry.issuingBody,
            section = entry.section,
            heading = entry.heading,
            body = "texto de prueba de ${entry.identifier}",
            officialUrl = entry.officialUrl,
            xmlUrl = entry.xmlUrl,
            pdfUrl = entry.pdfUrl,
            rank = null,
        )
    }

    private fun entry(id: String): SummaryEntry = SummaryEntry(
        identifier = id,
        control = null,
        title = "Publicación de prueba $id",
        publicationDate = DATE.toString(),
        section = BoeSectionDto.I,
        issuingBody = "MINISTERIO DE PRUEBA",
        heading = null,
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=$id",
        xmlUrl = "https://www.boe.es/diario_boe/xml.php?id=$id",
        pdfUrl = null,
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

    private fun DataSource.clean() {
        executeUpdate("DELETE FROM publicacion WHERE id LIKE ?") { statement ->
            statement.setString(1, PATTERN)
        }
    }

    private fun DataSource.count(): Long =
        queryRows(
            "SELECT count(*) AS total FROM publicacion WHERE id LIKE ?",
            { statement -> statement.setString(1, PATTERN) },
            { rows -> rows.getLong("total") },
        ).first()

    private fun DataSource.updatedAt(id: String): Instant =
        queryRows(
            "SELECT actualizado_en FROM publicacion WHERE id = ?",
            { statement -> statement.setString(1, id) },
            { rows -> rows.getObject("actualizado_en", OffsetDateTime::class.java).toInstant() },
        ).first()

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 10, 9)
        const val PATTERN = "FT00009-LIVE-%"
        const val ID_1 = "FT00009-LIVE-1"
        const val ID_2 = "FT00009-LIVE-2"
    }
}
