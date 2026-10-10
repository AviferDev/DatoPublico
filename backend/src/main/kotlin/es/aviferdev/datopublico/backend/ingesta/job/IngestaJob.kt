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
 * Resumen de una ejecución del [IngestaJob] para una fecha.
 *
 * @property fecha fecha ingerida.
 * @property totalEntradas entradas que devolvió el sumario del BOE.
 * @property guardadas publicaciones persistidas con éxito (*upsert*).
 * @property fallidas entradas que fallaron al parsear o guardar (no abortan el
 *   job).
 * @property error motivo si falló el sumario completo; `null` si se procesó.
 */
data class IngestaResult(
    val fecha: LocalDate,
    val totalEntradas: Int,
    val guardadas: Int,
    val fallidas: Int,
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
interface IngestaJob {
    /**
     * Ejecuta la ingesta de [fecha].
     *
     * Un fallo por entrada (p. ej. texto no disponible) se cuenta en
     * [IngestaResult.fallidas] y **no** aborta el resto; un fallo del sumario
     * completo se reporta en [IngestaResult.error] sin propagar.
     */
    suspend fun ejecutar(fecha: LocalDate): IngestaResult
}

/**
 * Implementación de [IngestaJob] sobre los colaboradores ya existentes
 * ([BoeSumarioClient], [BoePublicacionParser] y [PublicationRepository]).
 *
 * Los accesos a la base de datos son **bloqueantes** (JDBC) y se ejecutan en
 * [ioDispatcher] para no ocupar el hilo de eventos HTTP.
 *
 * @param sumarioClient cliente del sumario diario del BOE.
 * @param publicacionParser parser que descarga el texto y produce la publicación.
 * @param repositorio repositorio de publicaciones (*upsert* idempotente).
 * @param ioDispatcher dispatcher para el trabajo de E/S bloqueante.
 */
class IngestaJobDiario(
    private val sumarioClient: BoeSumarioClient,
    private val publicacionParser: BoePublicacionParser,
    private val repositorio: PublicationRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : IngestaJob {

    override suspend fun ejecutar(fecha: LocalDate): IngestaResult = withContext(ioDispatcher) {
        when (val sumario = obtenerSumario(fecha)) {
            is SumarioOutcome.Fallo -> IngestaResult(
                fecha = fecha,
                totalEntradas = 0,
                guardadas = 0,
                fallidas = 0,
                error = sumario.mensaje,
            )

            is SumarioOutcome.Exito -> registrarTodas(fecha, sumario.entradas)
        }
    }

    /** Persiste cada entrada contando éxitos y fallos; nunca aborta por una. */
    private suspend fun registrarTodas(
        fecha: LocalDate,
        entradas: List<EntradaSumario>,
    ): IngestaResult {
        var guardadas = 0
        var fallidas = 0
        for (entrada in entradas) {
            if (persistir(entrada)) {
                guardadas += 1
            } else {
                fallidas += 1
            }
        }
        return IngestaResult(
            fecha = fecha,
            totalEntradas = entradas.size,
            guardadas = guardadas,
            fallidas = fallidas,
        )
    }

    /** Parsea y guarda una entrada; `false` si falla (salvo cancelación). */
    private suspend fun persistir(entrada: EntradaSumario): Boolean = try {
        repositorio.save(publicacionParser.parsear(entrada))
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        false
    }

    /** Traduce el fallo del sumario a [SumarioOutcome.Fallo] (no propaga). */
    private suspend fun obtenerSumario(fecha: LocalDate): SumarioOutcome = try {
        SumarioOutcome.Exito(sumarioClient.obtenerSumario(fecha))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        SumarioOutcome.Fallo(
            error.message ?: "Fallo al obtener el sumario del BOE para $fecha"
        )
    }
}

/** Resultado interno de obtener el sumario: las entradas o el motivo del fallo. */
private sealed interface SumarioOutcome {
    data class Exito(val entradas: List<EntradaSumario>) : SumarioOutcome
    data class Fallo(val mensaje: String) : SumarioOutcome
}
