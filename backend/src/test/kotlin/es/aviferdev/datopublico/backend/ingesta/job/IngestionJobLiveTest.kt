package es.aviferdev.datopublico.backend.ingesta.job

import es.aviferdev.datopublico.backend.ingesta.publicacion.BoePublicacionParser
import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.ingesta.sumario.BoeSumarioClient
import es.aviferdev.datopublico.backend.ingesta.sumario.EntradaSumario
import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.backend.persistence.PublicationRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.executeUpdate
import es.aviferdev.datopublico.backend.persistence.queryRows
import es.aviferdev.datopublico.model.SeccionBoeDto
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
    private fun testSummary(): BoeSumarioClient = object : BoeSumarioClient {
        override suspend fun obtenerSumario(fecha: LocalDate): List<EntradaSumario> =
            listOf(entry(ID_1), entry(ID_2))
    }

    /** Parser de prueba: proyecta cada entrada a una [Publicacion] sin red. */
    private fun testParser(): BoePublicacionParser = object : BoePublicacionParser {
        override suspend fun parsear(entrada: EntradaSumario): Publicacion = Publicacion(
            id = entrada.identificador,
            titulo = entrada.titulo,
            fechaPublicacion = entrada.fechaPublicacion,
            organismo = entrada.organismo,
            seccion = entrada.seccion,
            epigrafe = entrada.epigrafe,
            texto = "texto de prueba de ${entrada.identificador}",
            urlOficial = entrada.urlOficial,
            urlXml = entrada.urlXml,
            urlPdf = entrada.urlPdf,
            rango = null,
        )
    }

    private fun entry(id: String): EntradaSumario = EntradaSumario(
        identificador = id,
        control = null,
        titulo = "Publicación de prueba $id",
        fechaPublicacion = DATE.toString(),
        seccion = SeccionBoeDto.I,
        organismo = "MINISTERIO DE PRUEBA",
        epigrafe = null,
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=$id",
        urlXml = "https://www.boe.es/diario_boe/xml.php?id=$id",
        urlPdf = null,
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
