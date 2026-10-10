package es.aviferdev.datopublico.backend.ingesta.backfill

import es.aviferdev.datopublico.backend.ingesta.job.DailyIngestionJob
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
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Prueba **opt-in** del backfill contra el PostgreSQL real de `docker-compose`
 * (escenario 9): rango corto con sumario/parser de prueba, interrupción simulada
 * por checkpoint y relanzamiento. Deja **una fila por `id`**, omite las fechas
 * completadas y refresca `actualizado_en` solo en las reprocesadas.
 *
 * Se salta por defecto (`DB_LIVE_TEST != 1`) para que el gate y la CI **no**
 * dependan de una base de datos. Requiere el esquema migrado
 * (`./gradlew :backend:flywayMigrate`):
 *
 * ```
 * DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*BackfillLiveTest'
 * ```
 */
class BackfillLiveTest {

    @Test
    fun `persists without duplicates and resumes skipping completed dates`() {
        if (System.getenv("DB_LIVE_TEST") != "1") {
            println("BackfillLiveTest omitido: exporta DB_LIVE_TEST=1 para el test real.")
        } else {
            runBlocking { executeLive() }
        }
    }

    private suspend fun executeLive() {
        val dir = Files.createTempDirectory("backfill-live-")
        val dataSource = Database.createDataSource(DatabaseConfig.fromEnv(environment()))
        try {
            dataSource.clean()
            checkResume(dataSource, dir)
        } finally {
            dataSource.clean()
            (dataSource as AutoCloseable).close()
            deleteRecursively(dir)
        }
    }

    private suspend fun checkResume(dataSource: DataSource, dir: Path) {
        val storeFile = dir.resolve("state.txt")
        val config = BackfillConfig(
            from = D0,
            to = D0.plusDays(2),
            batchDays = 1,
            delayMillis = 0L,
            stateFile = storeFile,
        )
        val runner = BackfillRunner(
            job = DailyIngestionJob(
                summaryClient = LiveSummaryClient(),
                publicationParser = LiveParser(),
                repository = PublicationRepositoryJdbc(dataSource),
            ),
            store = FileBackfillCheckpointStore(storeFile),
        )

        val first = runner.run(config)
        val updatedD0 = dataSource.updatedAt(id(D0))
        val beforeD1 = dataSource.updatedAt(id(D0.plusDays(1)))

        // Simula la interrupción tras el primer lote: solo D0 queda completada.
        FileBackfillCheckpointStore(storeFile).save(BackfillCheckpoint(D0, D0.plusDays(2), setOf(D0)))
        Thread.sleep(10)
        val second = runner.run(config)

        assertEquals(3, first.completed)
        assertEquals(3L, dataSource.count())
        assertEquals(1, second.skipped)
        assertEquals(2, second.completed)
        assertEquals(3L, dataSource.count(), "una fila por id, sin duplicados")
        assertEquals(updatedD0, dataSource.updatedAt(id(D0)), "la omitida no se reprocesa")
        assertTrue(
            dataSource.updatedAt(id(D0.plusDays(1))).isAfter(beforeD1),
            "actualizado_en avanza solo en las reprocesadas",
        )
    }

    private fun id(date: LocalDate): String = "FT00011-LIVE-$date"

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

    private fun DataSource.updatedAt(publicationId: String): Instant =
        queryRows(
            "SELECT actualizado_en FROM publicacion WHERE id = ?",
            { statement -> statement.setString(1, publicationId) },
            { rows -> rows.getObject("actualizado_en", OffsetDateTime::class.java).toInstant() },
        ).first()

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

    private fun deleteRecursively(dir: Path) {
        Files.walk(dir).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private companion object {
        val D0: LocalDate = LocalDate.of(2024, 1, 1)
        const val PATTERN = "FT00011-LIVE-%"
    }
}

/** Sumario de prueba: una entrada por fecha. */
private class LiveSummaryClient : BoeSumarioClient {
    override suspend fun obtenerSumario(fecha: LocalDate): List<EntradaSumario> =
        listOf(
            EntradaSumario(
                identificador = "FT00011-LIVE-$fecha",
                control = null,
                titulo = "Publicación de prueba $fecha",
                fechaPublicacion = fecha.toString(),
                seccion = SeccionBoeDto.I,
                organismo = "MINISTERIO DE PRUEBA",
                epigrafe = null,
                urlOficial = "https://www.boe.es/diario_boe/txt.php?id=FT00011-LIVE-$fecha",
                urlXml = null,
                urlPdf = null,
            )
        )
}

/** Parser de prueba: proyecta cada entrada a una publicación sin red. */
private class LiveParser : BoePublicacionParser {
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
