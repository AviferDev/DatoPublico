package es.aviferdev.datopublico.backend.ingesta.backfill

import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.backend.ingesta.job.DailyIngestionJob
import es.aviferdev.datopublico.backend.ingesta.publicacion.BoePublicacionHttpParser
import es.aviferdev.datopublico.backend.ingesta.publicacion.BoeTextoHttpClient
import es.aviferdev.datopublico.backend.ingesta.publicacion.boeTextoHttpClient
import es.aviferdev.datopublico.backend.ingesta.sumario.BoeSumarioHttpClient
import es.aviferdev.datopublico.backend.ingesta.sumario.boeSumarioHttpClient
import es.aviferdev.datopublico.backend.persistence.PublicationRepositoryJdbc
import io.ktor.client.HttpClient
import javax.sql.DataSource
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Entrada de línea de comandos del backfill *one-shot* (`:backend:backfill`).
 *
 * Compone el pool JDBC y los clientes HTTP (con `HttpTimeout`), reutiliza el
 * [DailyIngestionJob] y el [BackfillRunner], ejecuta el rango configurado y
 * **cierra** pool y clientes en un `finally`. Un fallo de configuración o de
 * base de datos sale con un mensaje claro y con código distinto de cero. No se
 * cablea en el arranque del servidor: se lanza a mano con su tarea Gradle.
 */
fun main() {
    val logger = LoggerFactory.getLogger(BACKFILL_LOGGER)
    val exitCode = try {
        val config = BackfillConfig.fromEnv()
        runBlocking { executeBackfill(config) }
        0
    } catch (error: Exception) {
        logger.error("Backfill abortado: {}", error.message)
        1
    }
    exitProcess(exitCode)
}

/**
 * Construye los recursos, ejecuta el backfill de [config] y los libera.
 *
 * @throws IllegalStateException si falta la configuración de base de datos.
 */
private suspend fun executeBackfill(config: BackfillConfig) {
    val dataSource = Database.createDataSource(DatabaseConfig.fromEnv())
    val summaryHttp = boeSumarioHttpClient()
    val textHttp = boeTextoHttpClient()
    try {
        val runner = BackfillRunner(
            job = createIngestionJob(dataSource, summaryHttp, textHttp),
            store = FileBackfillCheckpointStore(config.stateFile),
        )
        runner.run(config)
    } finally {
        summaryHttp.close()
        textHttp.close()
        (dataSource as? AutoCloseable)?.close()
    }
}

/** Reutiliza el job diario ya existente con los recursos del backfill. */
private fun createIngestionJob(
    dataSource: DataSource,
    summaryHttp: HttpClient,
    textHttp: HttpClient,
): DailyIngestionJob = DailyIngestionJob(
    summaryClient = BoeSumarioHttpClient(summaryHttp),
    publicationParser = BoePublicacionHttpParser(BoeTextoHttpClient(textHttp)),
    repository = PublicationRepositoryJdbc(dataSource),
)

/** Nombre del logger de la herramienta. */
private const val BACKFILL_LOGGER = "es.aviferdev.datopublico.backend.ingesta.backfill"
