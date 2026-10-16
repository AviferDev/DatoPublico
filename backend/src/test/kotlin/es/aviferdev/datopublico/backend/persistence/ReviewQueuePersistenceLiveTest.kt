package es.aviferdev.datopublico.backend.persistence

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.backend.rag.guardrails.GuardrailDecision
import es.aviferdev.datopublico.backend.rag.guardrails.SummaryGuardrailResult
import es.aviferdev.datopublico.backend.rag.guardrails.SummaryGuardrailViolation
import es.aviferdev.datopublico.backend.rag.review.SummaryDispositionService
import es.aviferdev.datopublico.backend.rag.review.SummaryReviewOutcome
import es.aviferdev.datopublico.backend.rag.review.SummaryState
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.SummaryDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import java.io.File
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Prueba **opt-in** contra el PostgreSQL real de `docker-compose` (escenario 7 del
 * spec): la disposición de FT00023 persiste el estado y la confianza, el resumen
 * publicado se lee `publicado`, el encolado aparece en `listPending()` y
 * reencolarlo **no** duplica la fila pendiente (índice único parcial de `V5`).
 *
 * Se salta por defecto (`DB_LIVE_TEST != 1`) para que el gate y la CI **no**
 * dependan de una base de datos. Requiere el esquema migrado (migración `V5`,
 * `./gradlew :backend:flywayMigrate`) y una `.env` o el entorno con `POSTGRES_*`.
 * Para ejecutarla:
 *
 * ```
 * DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*ReviewQueuePersistenceLiveTest'
 * ```
 */
class ReviewQueuePersistenceLiveTest {

    @Test
    fun `publishes one summary and queues another idempotently`() {
        if (System.getenv("DB_LIVE_TEST") != "1") {
            println("ReviewQueuePersistenceLiveTest omitido: exporta DB_LIVE_TEST=1 para el test real.")
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

    /** Publica un resumen, encola otro y comprueba el *round-trip* y la idempotencia. */
    private fun runLive(dataSource: DataSource) {
        val publications = PublicationRepositoryJdbc(dataSource)
        publications.save(publication(PUBLISHED_ID))
        publications.save(publication(QUEUED_ID))
        val service = SummaryDispositionService(
            SummaryRepositoryJdbc(dataSource),
            ReviewQueueRepositoryJdbc(dataSource),
        )

        val published = assertIs<SummaryReviewOutcome.Published>(
            service.dispose(publication(PUBLISHED_ID), summary(), result(GuardrailDecision.PUBLISHABLE, 0.95)),
        )
        val queued = assertIs<SummaryReviewOutcome.Queued>(
            service.dispose(
                publication(QUEUED_ID),
                summary(),
                result(GuardrailDecision.NEEDS_REVIEW, 0.6, listOf(SummaryGuardrailViolation.UNSUPPORTED_CLAIM)),
            ),
        )

        checkPublished(dataSource, published.summaryId)
        checkQueued(dataSource, queued.summaryId)
        checkRequeueIsIdempotent(dataSource, service, queued.summaryId)
    }

    /** El publicado se lee `publicado`, con su confianza y aviso de IA, y sin cola. */
    private fun checkPublished(dataSource: DataSource, summaryId: Long) {
        val row = SummaryRepositoryJdbc(dataSource).findByPublication(PUBLISHED_ID)

        assertEquals(SummaryState.PUBLISHED.persistedValue, row?.status)
        assertEquals(0.95, row?.confidence ?: 0.0, 1e-9)
        assertEquals(true, row?.aiNotice)
        assertEquals(0, ReviewQueueRepositoryJdbc(dataSource).listBySummary(summaryId).size)
        println("ReviewQueuePersistenceLiveTest: publicado OK (summaryId=$summaryId)")
    }

    /** El encolado aparece en `listPending()` con el motivo de las violaciones. */
    private fun checkQueued(dataSource: DataSource, summaryId: Long) {
        val pending = pendingOf(dataSource, summaryId)

        assertEquals(1, pending.size)
        assertEquals("UNSUPPORTED_CLAIM", pending.first().reason)
        println("ReviewQueuePersistenceLiveTest: encolado OK (summaryId=$summaryId)")
    }

    /** Reencolar actualiza el motivo y no duplica la fila pendiente. */
    private fun checkRequeueIsIdempotent(
        dataSource: DataSource,
        service: SummaryDispositionService,
        summaryId: Long,
    ) {
        service.dispose(
            publication(QUEUED_ID),
            summary(whatChanges = "Resumen actualizado"),
            result(GuardrailDecision.NEEDS_REVIEW, 0.6, listOf(SummaryGuardrailViolation.UNSUPPORTED_FIGURE)),
        )

        val pending = pendingOf(dataSource, summaryId)
        assertEquals(1, pending.size)
        assertEquals("UNSUPPORTED_FIGURE", pending.first().reason)
        println("ReviewQueuePersistenceLiveTest: reencolado idempotente OK (filas=${pending.size})")
    }

    private fun pendingOf(dataSource: DataSource, summaryId: Long): List<ReviewQueueEntity> =
        ReviewQueueRepositoryJdbc(dataSource).listPending().filter { row -> row.summaryId == summaryId }

    private fun result(
        decision: GuardrailDecision,
        groundingScore: Double,
        violations: List<SummaryGuardrailViolation> = emptyList(),
    ): SummaryGuardrailResult = SummaryGuardrailResult(
        decision = decision,
        contractViolations = emptyList(),
        guardrailViolations = violations,
        groundingScore = groundingScore,
    )

    private fun publication(id: String): Publicacion = Publicacion(
        id = id,
        title = "Resolución de prueba de la cola de revisión",
        publicationDate = DATE,
        issuingBody = null,
        section = SeccionBoeDto.II_A,
        heading = null,
        texto = null,
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=$id",
        urlXml = null,
        urlPdf = null,
        rango = null,
        category = CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS,
    )

    private fun summary(whatChanges: String = "Se aumenta el plazo de solicitud"): SummaryDto = SummaryDto(
        whatChanges = whatChanges,
        whoIsAffected = "A la ciudadanía",
        keyFigures = listOf("30 días"),
        officialSource = "https://www.boe.es/diario_boe/txt.php?id=$QUEUED_ID",
        aiNotice = true,
    )

    /** Borra las publicaciones de prueba (la cascada limpia `resumen` y `cola_revision`). */
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
        const val PUBLISHED_ID = "FT00023-LIVE-PUBLISHED"
        const val QUEUED_ID = "FT00023-LIVE-QUEUED"
    }
}
