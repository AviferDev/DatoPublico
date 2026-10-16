package es.aviferdev.datopublico.backend.rag.summary

import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.backend.ingesta.publication.Publicacion
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.generation.OpenCodeConfig
import es.aviferdev.datopublico.backend.rag.generation.openCodeHttpClient
import javax.sql.DataSource
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.exitProcess
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Resultado de procesar una publicación en la generación one-shot de resúmenes.
 *
 * [PUBLISHED], [QUEUED] y [DISCARDED] reflejan la disposición de FT00023;
 * [INVALID] es un resumen que no cumple el contrato (no se publica); [ALREADY_DONE]
 * indica que ya existía un resumen real y [FAILED] un fallo aislado (se continúa).
 */
internal enum class SummaryOutcome {
    PUBLISHED,
    QUEUED,
    DISCARDED,
    INVALID,
    ALREADY_DONE,
    FAILED,
}

/** Contadores agregados de una ejecución de generación. */
internal class GenerationTally {
    var published: Int = 0
    var queued: Int = 0
    var discarded: Int = 0
    var invalid: Int = 0
    var alreadyDone: Int = 0
    var failed: Int = 0

    /** Suma [outcome] al contador correspondiente. */
    fun record(outcome: SummaryOutcome) {
        when (outcome) {
            SummaryOutcome.PUBLISHED -> published += 1
            SummaryOutcome.QUEUED -> queued += 1
            SummaryOutcome.DISCARDED -> discarded += 1
            SummaryOutcome.INVALID -> invalid += 1
            SummaryOutcome.ALREADY_DONE -> alreadyDone += 1
            SummaryOutcome.FAILED -> failed += 1
        }
    }
}

/**
 * Entrada de línea de comandos de la **generación one-shot de resúmenes
 * ciudadanos** reales (`:backend:generateSummaries`).
 *
 * Resuelve la conexión (`POSTGRES_*`), la clave/config de OpenCode
 * (`OPENCODE_API_KEY`, fail-fast) y el modelo E5 (ya descargado), recorre las
 * publicaciones del rango configurado, **indexa** sus fragmentos, **genera** el
 * resumen con IA, lo **valida** (contrato + guardrails anti-alucinación) y lo
 * **publica** en `resumen` (estado `publicado`) o lo descarta/encola. Es un
 * *upsert* por publicación, así que **sobrescribe** cualquier resumen previo
 * (incluidos los *seed* de desarrollo) y es reejecutable sin duplicar.
 *
 * No se cablea en el arranque del servidor: se lanza a mano con su tarea Gradle.
 * Un fallo de configuración cierra con código ≠ 0 y mensaje claro; el pool, el
 * modelo y el cliente HTTP se cierran siempre.
 */
fun main() {
    val logger = LoggerFactory.getLogger(LOGGER_NAME)
    val exitCode = try {
        runBlocking { runGeneration(logger) }
        EXIT_OK
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        logger.error("Generación de resúmenes abortada: {}", error.message)
        EXIT_FAILURE
    }
    exitProcess(exitCode)
}

/** Construye los recursos (pool, modelo, cliente) y ejecuta la generación. */
private suspend fun runGeneration(logger: Logger) {
    val config = GenerateSummariesConfig.fromEnv()
    val openCode = OpenCodeConfig.fromEnv()
    val dataSource = Database.createDataSource(DatabaseConfig.fromEnv())
    try {
        val provider = createEmbeddingProvider()
        try {
            generate(logger, config, openCode, dataSource, provider)
        } finally {
            provider.close()
        }
    } finally {
        (dataSource as? AutoCloseable)?.close()
    }
}

/** Compone el pipeline, recorre el rango y registra el balance de la ejecución. */
private suspend fun generate(
    logger: Logger,
    config: GenerateSummariesConfig,
    openCode: OpenCodeConfig,
    dataSource: DataSource,
    provider: EmbeddingProvider,
) {
    val client = openCodeHttpClient()
    try {
        val pipeline = createPipeline(dataSource, provider, openCode, client)
        val tally = GenerationTally()
        processDates(pipeline, config, tally, logger)
        logCompleted(logger, config, tally)
    } finally {
        client.close()
    }
}

/** Recorre las fechas del rango hasta agotar el tope de publicaciones configurado. */
private suspend fun processDates(
    pipeline: SummaryGenerationPipeline,
    config: GenerateSummariesConfig,
    tally: GenerationTally,
    logger: Logger,
) {
    var remaining = config.limit
    for (date in config.dates()) {
        if (remaining <= 0) break
        val publications = pipeline.publications.listByDate(date.toString())
        remaining = processPublications(publications, pipeline, remaining, config, tally, logger)
    }
}

/** Procesa las publicaciones de una fecha hasta agotar el tope; devuelve el restante. */
private suspend fun processPublications(
    publications: List<Publicacion>,
    pipeline: SummaryGenerationPipeline,
    remaining: Int,
    config: GenerateSummariesConfig,
    tally: GenerationTally,
    logger: Logger,
): Int {
    var left = remaining
    for (publication in publications) {
        if (left <= 0) break
        left = processOne(pipeline, publication, left, tally, logger)
        delay(config.delayMillis)
    }
    return left
}

/** Procesa una publicación; descuenta tope solo si realmente se genera (no si ya existe). */
private suspend fun processOne(
    pipeline: SummaryGenerationPipeline,
    publication: Publicacion,
    remaining: Int,
    tally: GenerationTally,
    logger: Logger,
): Int {
    val consumed = if (hasRealSummary(pipeline, publication)) {
        tally.record(SummaryOutcome.ALREADY_DONE)
        BUDGET_UNUSED
    } else {
        tally.record(attempt(pipeline, publication, logger))
        BUDGET_USED
    }
    return remaining - consumed
}

/** Ejecuta el pipeline capturando fallos por publicación (no abortan la ejecución). */
private suspend fun attempt(
    pipeline: SummaryGenerationPipeline,
    publication: Publicacion,
    logger: Logger,
): SummaryOutcome {
    val outcome = try {
        processPublication(pipeline, publication)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        logger.warn("Resumen fallido id={} error={}", publication.id, error.message)
        SummaryOutcome.FAILED
    }
    logger.info("Resumen procesado id={} resultado={}", publication.id, outcome)
    return outcome
}

/** Registra el balance agregado de la ejecución como una línea JSON estructurada. */
private fun logCompleted(logger: Logger, config: GenerateSummariesConfig, tally: GenerationTally) {
    logger.info(
        "summary.generation.completed from={} to={} published={} queued={} discarded={} " +
            "invalid={} already_done={} failed={}",
        config.from,
        config.to,
        tally.published,
        tally.queued,
        tally.discarded,
        tally.invalid,
        tally.alreadyDone,
        tally.failed,
    )
}

/** Nombre del logger de la herramienta. */
private const val LOGGER_NAME = "es.aviferdev.datopublico.backend.rag.summary"

/** Código de salida cuando la generación termina sin abortar. */
private const val EXIT_OK = 0

/** Código de salida cuando la generación se aborta. */
private const val EXIT_FAILURE = 1

/** No descuenta tope (publicación ya resumida). */
private const val BUDGET_UNUSED = 0

/** Descuenta una unidad de tope. */
private const val BUDGET_USED = 1
