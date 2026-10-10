package es.aviferdev.datopublico.backend.ingesta.backfill

import es.aviferdev.datopublico.backend.ingesta.job.DailyIngestionJob
import es.aviferdev.datopublico.backend.ingesta.job.IngestionJob
import es.aviferdev.datopublico.backend.ingesta.job.IngestionResult
import es.aviferdev.datopublico.backend.ingesta.publicacion.BoePublicacionParser
import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.ingesta.sumario.BoeSumarioClient
import es.aviferdev.datopublico.backend.ingesta.sumario.EntradaSumario
import es.aviferdev.datopublico.backend.persistence.PublicationRepository
import es.aviferdev.datopublico.model.SeccionBoeDto
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Tests de gate de [BackfillRunner] con **dobles** (sin red ni base de datos):
 * idempotencia real del *upsert* con [DailyIngestionJob] y un repositorio en
 * memoria (escenario 2), reanudación por checkpoint (escenario 3), fallo de una
 * fecha aislado y reintentado (escenario 4), pausa con tiempo virtual y E/S en
 * `Dispatchers.IO` (escenario 6) y las **ocho** secciones sin filtrar
 * (escenario 7).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackfillRunnerTest {

    @Test
    fun `runs the range once and does not duplicate on a second pass`() = runTest {
        val repository = InMemoryPublicationRepository()
        val runner = BackfillRunner(
            job = realJob(repository) { date -> listOf(entry("BOE-$date", date)) },
            store = RecordingCheckpointStore(),
        )
        val config = config(D0, D0.plusDays(1), batchDays = 7)

        val first = runner.run(config)
        val second = runner.run(config)

        assertEquals(2, first.completed)
        assertEquals(2, first.saved)
        assertEquals(0, first.failed)
        assertEquals(2, second.skipped)
        assertEquals(0, second.completed)
        assertEquals(2, repository.saved.size, "el upsert no duplica filas")
    }

    @Test
    fun `resumes from a matching checkpoint and skips the completed dates`() = runTest {
        val job = RecordingJob()
        val checkpoint = BackfillCheckpoint(D0, D0.plusDays(4), setOf(D0, D0.plusDays(1)))
        val runner = BackfillRunner(job = job, store = RecordingCheckpointStore(checkpoint))

        val result = runner.run(config(D0, D0.plusDays(4), batchDays = 2))

        assertEquals(listOf(D0.plusDays(2), D0.plusDays(3), D0.plusDays(4)), job.executed)
        assertEquals(2, result.skipped)
        assertEquals(3, result.completed)
    }

    @Test
    fun `starts from scratch when the checkpoint range does not match`() = runTest {
        val job = RecordingJob()
        val stale = BackfillCheckpoint(D0.minusDays(1), D0, setOf(D0.minusDays(1)))
        val runner = BackfillRunner(job = job, store = RecordingCheckpointStore(stale))

        val result = runner.run(config(D0, D0.plusDays(1), batchDays = 2))

        assertEquals(0, result.skipped)
        assertEquals(2, result.completed)
        assertEquals(2, job.executed.size)
    }

    @Test
    fun `persists the checkpoint after each batch`() = runTest {
        val store = RecordingCheckpointStore()
        val runner = BackfillRunner(job = RecordingJob(), store = store)

        runner.run(config(D0, D0.plusDays(4), batchDays = 2))

        assertEquals(3, store.saves.size, "un guardado por lote")
        assertEquals(setOf(D0, D0.plusDays(1)), store.saves.first().completedDates)
        assertEquals(days(0, 4).toSet(), store.saves.last().completedDates)
    }

    @Test
    fun `a failing date is isolated and retried on the next run`() = runTest {
        val failing = mapOf(
            D0.plusDays(1) to IngestionResult(D0.plusDays(1), 0, 0, 0, error = "BOE caído")
        )
        val job = RecordingJob(outcomes = failing)
        val runner = BackfillRunner(job = job, store = RecordingCheckpointStore())
        val config = config(D0, D0.plusDays(2), batchDays = 3)

        val first = runner.run(config)
        val second = runner.run(config)

        assertEquals(2, first.completed)
        assertEquals(1, first.failed)
        assertEquals(0, first.skipped)
        assertEquals(2, second.skipped)
        assertEquals(0, second.completed)
        assertEquals(1, second.failed)
        assertEquals(listOf(D0, D0.plusDays(1), D0.plusDays(2), D0.plusDays(1)), job.executed)
    }

    @Test
    fun `a job exception does not abort the run`() = runTest {
        val job = RecordingJob(failing = setOf(D0.plusDays(1)))
        val runner = BackfillRunner(job = job, store = RecordingCheckpointStore())

        val result = runner.run(config(D0, D0.plusDays(2), batchDays = 3))

        assertEquals(2, result.completed)
        assertEquals(1, result.failed)
    }

    @Test
    fun `respects the configured pause between dates using virtual time`() = runTest {
        val job = RecordingJob()
        val runner = BackfillRunner(job = job, store = RecordingCheckpointStore())

        runner.run(config(D0, D0.plusDays(4), batchDays = 5, delayMillis = 1_000L))

        assertEquals(5, job.executed.size)
        assertEquals(4_000L, testScheduler.currentTime, "4 pausas de 1 s entre 5 fechas")
    }

    @Test
    fun `runs the job on the injected io dispatcher`() = runTest {
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, IO_THREAD) }
        executor.asCoroutineDispatcher().use { dispatcher ->
            val threads = mutableListOf<String>()
            val job = object : IngestionJob {
                override suspend fun run(date: LocalDate): IngestionResult {
                    threads += Thread.currentThread().name
                    return IngestionResult(date, 0, saved = 1, failed = 0)
                }
            }
            BackfillRunner(job = job, store = RecordingCheckpointStore(), ioDispatcher = dispatcher)
                .run(config(D0, D0, batchDays = 1, delayMillis = 0L))

            assertTrue(threads.all { name -> name.startsWith(IO_THREAD) }, "$threads")
        }
    }

    @Test
    fun `persists the eight sections without filtering`() = runTest {
        val repository = InMemoryPublicationRepository()
        val runner = BackfillRunner(
            job = realJob(repository) { date ->
                SeccionBoeDto.values().map { section -> entry("BOE-${section.name}-$date", date, section) }
            },
            store = RecordingCheckpointStore(),
        )

        val result = runner.run(config(D0, D0, batchDays = 1))

        assertEquals(8, result.saved)
        assertEquals(8, repository.saved.size)
        assertEquals(
            SeccionBoeDto.values().map { section -> section.name }.toSet(),
            repository.saved.values.map { publication -> publication.seccion.name }.toSet(),
        )
    }

    private fun config(
        from: LocalDate,
        to: LocalDate,
        batchDays: Int = 7,
        delayMillis: Long = 0L,
    ): BackfillConfig = BackfillConfig(
        from = from,
        to = to,
        batchDays = batchDays,
        delayMillis = delayMillis,
        stateFile = Path.of(".backfill-test-state.txt"),
    )

    private fun realJob(
        repository: PublicationRepository,
        entriesFor: (LocalDate) -> List<EntradaSumario>,
    ): DailyIngestionJob = DailyIngestionJob(
        summaryClient = object : BoeSumarioClient {
            override suspend fun obtenerSumario(fecha: LocalDate): List<EntradaSumario> =
                entriesFor(fecha)
        },
        publicationParser = TestParser(),
        repository = repository,
    )

    private fun days(first: Long, last: Long): List<LocalDate> =
        (first..last).map { offset -> D0.plusDays(offset) }

    private fun entry(id: String, date: LocalDate, section: SeccionBoeDto = SeccionBoeDto.I) =
        EntradaSumario(
            identificador = id,
            control = null,
            titulo = "Título $id",
            fechaPublicacion = date.toString(),
            seccion = section,
            organismo = "MINISTERIO X",
            epigrafe = null,
            urlOficial = "https://www.boe.es/diario_boe/txt.php?id=$id",
            urlXml = "https://www.boe.es/diario_boe/xml.php?id=$id",
            urlPdf = null,
        )

    private companion object {
        val D0: LocalDate = LocalDate.of(2024, 1, 1)
        const val IO_THREAD = "backfill-io-test"
    }
}

/** Doble del job: registra fechas, falla las indicadas y usa [outcomes] si están. */
private class RecordingJob(
    private val outcomes: Map<LocalDate, IngestionResult> = emptyMap(),
    private val failing: Set<LocalDate> = emptySet(),
) : IngestionJob {
    val executed = mutableListOf<LocalDate>()

    override suspend fun run(date: LocalDate): IngestionResult {
        executed += date
        if (date in failing) {
            throw IllegalStateException("fallo de job para $date")
        }
        return outcomes[date] ?: IngestionResult(date, totalEntries = 0, saved = 1, failed = 0)
    }
}

/** Checkpoint en memoria: recuerda el último estado y todas las escrituras. */
private class RecordingCheckpointStore(
    initial: BackfillCheckpoint? = null,
) : BackfillCheckpointStore {
    private var current: BackfillCheckpoint? = initial
    val saves = mutableListOf<BackfillCheckpoint>()

    override fun load(): BackfillCheckpoint? = current

    override fun save(checkpoint: BackfillCheckpoint) {
        current = checkpoint
        saves += checkpoint
    }
}

/** Repositorio en memoria con *upsert* por `id`. */
private class InMemoryPublicationRepository : PublicationRepository {
    val saved = linkedMapOf<String, Publicacion>()

    override fun save(publication: Publicacion): Publicacion {
        saved[publication.id] = publication
        return publication
    }

    override fun findById(id: String): Publicacion? = saved[id]

    override fun listByDate(publicationDate: String): List<Publicacion> =
        saved.values.filter { publication -> publication.fechaPublicacion == publicationDate }
}

/** Parser de prueba: proyecta la entrada a una publicación sin red. */
private class TestParser : BoePublicacionParser {
    override suspend fun parsear(entrada: EntradaSumario): Publicacion = Publicacion(
        id = entrada.identificador,
        titulo = entrada.titulo,
        fechaPublicacion = entrada.fechaPublicacion,
        organismo = entrada.organismo,
        seccion = entrada.seccion,
        epigrafe = entrada.epigrafe,
        texto = "texto de ${entrada.identificador}",
        urlOficial = entrada.urlOficial,
        urlXml = entrada.urlXml,
        urlPdf = entrada.urlPdf,
        rango = null,
    )
}
