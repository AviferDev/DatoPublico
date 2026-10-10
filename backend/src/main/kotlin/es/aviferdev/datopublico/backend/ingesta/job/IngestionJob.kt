package es.aviferdev.datopublico.backend.ingesta.job

import es.aviferdev.datopublico.backend.ingesta.publicacion.BoePublicacionParser
import es.aviferdev.datopublico.backend.ingesta.sumario.BoeSumarioClient
import es.aviferdev.datopublico.backend.ingesta.sumario.EntradaSumario
import es.aviferdev.datopublico.backend.persistence.PublicationRepository
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resumen de una ejecución del [IngestionJob] para una fecha.
 *
 * @property date fecha ingerida.
 * @property totalEntries entradas que devolvió el sumario del BOE.
 * @property saved publicaciones persistidas con éxito (*upsert*).
 * @property failed entradas que fallaron al parsear o guardar (no abortan el
 *   job).
 * @property error motivo si falló el sumario completo; `null` si se procesó.
 */
data class IngestionResult(
    val date: LocalDate,
    val totalEntries: Int,
    val saved: Int,
    val failed: Int,
    val error: String? = null,
)

/**
 * Orquestador del job diario de ingesta: descarga el sumario, parsea cada entrada
 * y persiste la publicación de forma **idempotente**.
 *
 * Reejecutar la misma fecha **no duplica** filas: `PublicationRepository.save` es
 * un *upsert* por `id`. [es.aviferdev.datopublico.model.CategoriaDto] y
 * [es.aviferdev.datopublico.model.PlazoDto] llegan siempre `null` aquí; los
 * clasifica FT00012.
 */
interface IngestionJob {
    /**
     * Ejecuta la ingesta de [date].
     *
     * Un fallo por entrada (p. ej. texto no disponible) se cuenta en
     * [IngestionResult.failed] y **no** aborta el resto; un fallo del sumario
     * completo se reporta en [IngestionResult.error] sin propagar.
     */
    suspend fun run(date: LocalDate): IngestionResult
}

/**
 * Implementación de [IngestionJob] sobre los colaboradores ya existentes
 * ([BoeSumarioClient], [BoePublicacionParser] y [PublicationRepository]).
 *
 * Los accesos a la base de datos son **bloqueantes** (JDBC) y se ejecutan en
 * [ioDispatcher] para no ocupar el hilo de eventos HTTP.
 *
 * @param summaryClient cliente del sumario diario del BOE.
 * @param publicationParser parser que descarga el texto y produce la publicación.
 * @param repository repositorio de publicaciones (*upsert* idempotente).
 * @param ioDispatcher dispatcher para el trabajo de E/S bloqueante.
 */
class DailyIngestionJob(
    private val summaryClient: BoeSumarioClient,
    private val publicationParser: BoePublicacionParser,
    private val repository: PublicationRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : IngestionJob {

    override suspend fun run(date: LocalDate): IngestionResult = withContext(ioDispatcher) {
        when (val summary = fetchSummary(date)) {
            is SummaryOutcome.Failure -> IngestionResult(
                date = date,
                totalEntries = 0,
                saved = 0,
                failed = 0,
                error = summary.message,
            )

            is SummaryOutcome.Success -> saveAll(date, summary.entries)
        }
    }

    /** Persiste cada entrada contando éxitos y fallos; nunca aborta por una. */
    private suspend fun saveAll(
        date: LocalDate,
        entries: List<EntradaSumario>,
    ): IngestionResult {
        var saved = 0
        var failed = 0
        for (entry in entries) {
            if (persist(entry)) {
                saved += 1
            } else {
                failed += 1
            }
        }
        return IngestionResult(
            date = date,
            totalEntries = entries.size,
            saved = saved,
            failed = failed,
        )
    }

    /** Parsea y guarda una entrada; `false` si falla (salvo cancelación). */
    private suspend fun persist(entry: EntradaSumario): Boolean = try {
        repository.save(publicationParser.parsear(entry))
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        false
    }

    /** Traduce el fallo del sumario a [SummaryOutcome.Failure] (no propaga). */
    private suspend fun fetchSummary(date: LocalDate): SummaryOutcome = try {
        SummaryOutcome.Success(summaryClient.obtenerSumario(date))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        SummaryOutcome.Failure(
            error.message ?: "Fallo al obtener el sumario del BOE para $date"
        )
    }
}

/** Resultado interno de obtener el sumario: las entradas o el motivo del fallo. */
private sealed interface SummaryOutcome {
    data class Success(val entries: List<EntradaSumario>) : SummaryOutcome
    data class Failure(val message: String) : SummaryOutcome
}
