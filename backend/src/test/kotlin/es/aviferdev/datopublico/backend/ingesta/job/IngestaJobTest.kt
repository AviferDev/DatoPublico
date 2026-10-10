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
 * Tests de gate de [IngestaJobDiario] con **dobles** (sin red ni base de datos):
 * orquestación descarga → parseo → persistencia (escenario 2), idempotencia por
 * *upsert* con repositorio en memoria (escenario 3) y fallo parcial que no aborta
 * el job (escenario 4).
 */
class IngestaJobTest {

    private val fecha = LocalDate.of(2026, 10, 9)

    @Test
    fun `orquesta descarga parseo y persistencia de cada entrada`() = runTest {
        val repositorio = FakePublicationRepository()
        val job = jobCon(
            entradas = listOf(entrada("BOE-A-1"), entrada("BOE-A-2"), entrada("BOE-A-3")),
            parser = FakeParser(fallan = emptySet()),
            repositorio = repositorio,
        )

        val resultado = job.ejecutar(fecha)

        assertEquals(3, resultado.totalEntradas)
        assertEquals(3, resultado.guardadas)
        assertEquals(0, resultado.fallidas)
        assertEquals(null, resultado.error)
        assertEquals(setOf("BOE-A-1", "BOE-A-2", "BOE-A-3"), repositorio.saved.keys)
    }

    @Test
    fun `ejecutar dos veces la misma fecha no duplica publicaciones`() = runTest {
        val repositorio = FakePublicationRepository()
        val job = jobCon(
            entradas = listOf(entrada("BOE-A-1"), entrada("BOE-A-2")),
            parser = FakeParser(fallan = emptySet()),
            repositorio = repositorio,
        )

        job.ejecutar(fecha)
        val segunda = job.ejecutar(fecha)

        assertEquals(2, repositorio.saved.size, "el upsert no debe duplicar filas")
        assertEquals(2, segunda.guardadas)
        assertEquals(2, repositorio.saveCounts["BOE-A-1"], "cada guardado reaplica el mismo id")
    }

    @Test
    fun `un fallo por entrada no aborta el resto del job`() = runTest {
        val repositorio = FakePublicationRepository()
        val job = jobCon(
            entradas = listOf(entrada("BOE-A-1"), entrada("BOE-A-2"), entrada("BOE-A-3")),
            parser = FakeParser(fallan = setOf("BOE-A-2")),
            repositorio = repositorio,
        )

        val resultado = job.ejecutar(fecha)

        assertEquals(3, resultado.totalEntradas)
        assertEquals(2, resultado.guardadas)
        assertEquals(1, resultado.fallidas)
        assertEquals(setOf("BOE-A-1", "BOE-A-3"), repositorio.saved.keys)
    }

    @Test
    fun `un fallo de repositorio cuenta como fallida sin abortar`() = runTest {
        val repositorio = FakePublicationRepository(failOnId = "BOE-A-2")
        val job = jobCon(
            entradas = listOf(entrada("BOE-A-1"), entrada("BOE-A-2")),
            parser = FakeParser(fallan = emptySet()),
            repositorio = repositorio,
        )

        val resultado = job.ejecutar(fecha)

        assertEquals(1, resultado.guardadas)
        assertEquals(1, resultado.fallidas)
    }

    @Test
    fun `un fallo del sumario se reporta sin propagar`() = runTest {
        val job = IngestaJobDiario(
            sumarioClient = FakeSumarioClient(error = BoeSumarioException("BOE caído")),
            publicacionParser = FakeParser(fallan = emptySet()),
            repositorio = FakePublicationRepository(),
        )

        val resultado = job.ejecutar(fecha)

        assertEquals(0, resultado.totalEntradas)
        assertEquals(0, resultado.guardadas)
        assertEquals(0, resultado.fallidas)
        assertTrue(resultado.error!!.contains("BOE caído"), resultado.error!!)
    }

    private fun jobCon(
        entradas: List<EntradaSumario>,
        parser: BoePublicacionParser,
        repositorio: PublicationRepository,
    ): IngestaJob = IngestaJobDiario(
        sumarioClient = FakeSumarioClient(entradas = entradas),
        publicacionParser = parser,
        repositorio = repositorio,
    )

    private fun entrada(id: String): EntradaSumario = EntradaSumario(
        identificador = id,
        control = null,
        titulo = "Título de $id",
        fechaPublicacion = fecha.toString(),
        seccion = SeccionBoeDto.I,
        organismo = "MINISTERIO X",
        epigrafe = null,
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=$id",
        urlXml = "https://www.boe.es/diario_boe/xml.php?id=$id",
        urlPdf = null,
    )

    /** Doble del sumario: devuelve las entradas o lanza el error configurado. */
    private class FakeSumarioClient(
        private val entradas: List<EntradaSumario> = emptyList(),
        private val error: BoeSumarioException? = null,
    ) : BoeSumarioClient {
        override suspend fun obtenerSumario(fecha: LocalDate): List<EntradaSumario> =
            error?.let { throw it } ?: entradas
    }

    /** Doble del parser: produce una publicación, salvo para los ids de [fallan]. */
    private class FakeParser(private val fallan: Set<String>) : BoePublicacionParser {
        override suspend fun parsear(entrada: EntradaSumario): Publicacion {
            if (entrada.identificador in fallan) {
                throw BoeTextoException("sin texto para ${entrada.identificador}")
            }
            return publicacionDe(entrada)
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
        fun publicacionDe(entrada: EntradaSumario): Publicacion = Publicacion(
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
