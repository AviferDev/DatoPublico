package es.aviferdev.datopublico.backend.ingesta.job

import es.aviferdev.datopublico.backend.ingesta.publicacion.BoePublicacionParser
import es.aviferdev.datopublico.backend.ingesta.publicacion.BoeTextoException
import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.ingesta.sumario.BoeSumarioClient
import es.aviferdev.datopublico.backend.ingesta.sumario.BoeSumarioException
import es.aviferdev.datopublico.backend.ingesta.sumario.EntradaSumario
import es.aviferdev.datopublico.backend.persistence.PublicationRepository
import es.aviferdev.datopublico.model.SeccionBoeDto
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Tests de gate de [DailyIngestionJob] con **dobles** (sin red ni base de datos):
 * orquestación descarga → parseo → persistencia (escenario 2), idempotencia por
 * *upsert* con repositorio en memoria (escenario 3) y fallo parcial que no aborta
 * el job (escenario 4).
 */
class IngestionJobTest {

    private val date = LocalDate.of(2026, 10, 9)

    @Test
    fun `orchestrates download parsing and persistence of each entry`() = runTest {
        val repository = FakePublicationRepository()
        val job = jobWith(
            entries = listOf(entry("BOE-A-1"), entry("BOE-A-2"), entry("BOE-A-3")),
            parser = FakeParser(failing = emptySet()),
            repository = repository,
        )

        val result = job.run(date)

        assertEquals(3, result.totalEntries)
        assertEquals(3, result.saved)
        assertEquals(0, result.failed)
        assertEquals(null, result.error)
        assertEquals(setOf("BOE-A-1", "BOE-A-2", "BOE-A-3"), repository.saved.keys)
    }

    @Test
    fun `running twice for the same date does not duplicate publications`() = runTest {
        val repository = FakePublicationRepository()
        val job = jobWith(
            entries = listOf(entry("BOE-A-1"), entry("BOE-A-2")),
            parser = FakeParser(failing = emptySet()),
            repository = repository,
        )

        job.run(date)
        val second = job.run(date)

        assertEquals(2, repository.saved.size, "el upsert no debe duplicar filas")
        assertEquals(2, second.saved)
        assertEquals(2, repository.saveCounts["BOE-A-1"], "cada guardado reaplica el mismo id")
    }

    @Test
    fun `a per-entry failure does not abort the rest of the job`() = runTest {
        val repository = FakePublicationRepository()
        val job = jobWith(
            entries = listOf(entry("BOE-A-1"), entry("BOE-A-2"), entry("BOE-A-3")),
            parser = FakeParser(failing = setOf("BOE-A-2")),
            repository = repository,
        )

        val result = job.run(date)

        assertEquals(3, result.totalEntries)
        assertEquals(2, result.saved)
        assertEquals(1, result.failed)
        assertEquals(setOf("BOE-A-1", "BOE-A-3"), repository.saved.keys)
    }

    @Test
    fun `a repository failure counts as failed without aborting`() = runTest {
        val repository = FakePublicationRepository(failOnId = "BOE-A-2")
        val job = jobWith(
            entries = listOf(entry("BOE-A-1"), entry("BOE-A-2")),
            parser = FakeParser(failing = emptySet()),
            repository = repository,
        )

        val result = job.run(date)

        assertEquals(1, result.saved)
        assertEquals(1, result.failed)
    }

    @Test
    fun `a summary failure is reported without propagating`() = runTest {
        val job = DailyIngestionJob(
            summaryClient = FakeSummaryClient(error = BoeSumarioException("BOE caído")),
            publicationParser = FakeParser(failing = emptySet()),
            repository = FakePublicationRepository(),
        )

        val result = job.run(date)

        assertEquals(0, result.totalEntries)
        assertEquals(0, result.saved)
        assertEquals(0, result.failed)
        assertTrue(result.error!!.contains("BOE caído"), result.error!!)
    }

    private fun jobWith(
        entries: List<EntradaSumario>,
        parser: BoePublicacionParser,
        repository: PublicationRepository,
    ): IngestionJob = DailyIngestionJob(
        summaryClient = FakeSummaryClient(entries = entries),
        publicationParser = parser,
        repository = repository,
    )

    private fun entry(id: String): EntradaSumario = EntradaSumario(
        identificador = id,
        control = null,
        titulo = "Título de $id",
        fechaPublicacion = date.toString(),
        seccion = SeccionBoeDto.I,
        organismo = "MINISTERIO X",
        epigrafe = null,
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=$id",
        urlXml = "https://www.boe.es/diario_boe/xml.php?id=$id",
        urlPdf = null,
    )

    /** Doble del sumario: devuelve las entradas o lanza el error configurado. */
    private class FakeSummaryClient(
        private val entries: List<EntradaSumario> = emptyList(),
        private val error: BoeSumarioException? = null,
    ) : BoeSumarioClient {
        override suspend fun obtenerSumario(fecha: LocalDate): List<EntradaSumario> =
            error?.let { throw it } ?: entries
    }

    /** Doble del parser: produce una publicación, salvo para los ids de [failing]. */
    private class FakeParser(private val failing: Set<String>) : BoePublicacionParser {
        override suspend fun parsear(entrada: EntradaSumario): Publicacion {
            if (entrada.identificador in failing) {
                throw BoeTextoException("sin texto para ${entrada.identificador}")
            }
            return publicationFrom(entrada)
        }
    }

    /** Repositorio en memoria con *upsert* por `id` y recuento de guardados. */
    private class FakePublicationRepository(
        private val failOnId: String? = null,
    ) : PublicationRepository {
        val saved = mutableMapOf<String, Publicacion>()
        val saveCounts = mutableMapOf<String, Int>()

        override fun save(publication: Publicacion): Publicacion {
            if (publication.id == failOnId) {
                throw IllegalStateException("fallo de repositorio para ${publication.id}")
            }
            saved[publication.id] = publication
            saveCounts.merge(publication.id, 1, Int::plus)
            return publication
        }

        override fun findById(id: String): Publicacion? = saved[id]

        override fun listByDate(publicationDate: String): List<Publicacion> =
            saved.values.filter { it.fechaPublicacion == publicationDate }
    }

    private companion object {
        /** Proyecta la entrada a la publicación que produciría el parser. */
        fun publicationFrom(entrada: EntradaSumario): Publicacion = Publicacion(
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
}
