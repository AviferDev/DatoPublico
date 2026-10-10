package es.aviferdev.datopublico.backend.rag.summary

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.persistence.FragmentMatch
import es.aviferdev.datopublico.backend.persistence.FragmentRepository
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.generation.OpenCodeConfig
import es.aviferdev.datopublico.backend.rag.generation.OpenCodeTextGenerationProvider
import es.aviferdev.datopublico.backend.rag.generation.openCodeHttpClient
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Prueba **opt-in** contra el endpoint **real de OpenCode** (escenario 9 del spec):
 * genera un resumen ciudadano con fragmentos de prueba **en memoria** (sin
 * PostgreSQL ni ONNX) y comprueba que es válido y lleva enlace a la fuente.
 *
 * Se salta por defecto (sin `OPENCODE_LIVE_TEST=1`), de modo que el gate y la CI
 * **no** abren sockets. Con `OPENCODE_LIVE_TEST=1` pero **sin** `OPENCODE_API_KEY`,
 * el test falla en claro (fail-fast de [OpenCodeConfig.fromEnv]).
 *
 * ```
 * OPENCODE_LIVE_TEST=1 OPENCODE_API_KEY=<clave> \
 *   ./gradlew :backend:test --tests '*CitizenSummaryGeneratorLiveTest'
 * ```
 */
class CitizenSummaryGeneratorLiveTest {

    @Test
    fun `generates a valid summary against the real OpenCode endpoint`() = runTest {
        if (System.getenv("OPENCODE_LIVE_TEST") != "1") {
            println(
                "CitizenSummaryGeneratorLiveTest omitido: exporta OPENCODE_LIVE_TEST=1 " +
                    "y OPENCODE_API_KEY para ejecutarlo contra OpenCode."
            )
        } else {
            runLive()
        }
    }

    /** Ejecuta la generación real y afirma que el resumen es válido con su fuente. */
    private suspend fun runLive() {
        val config = OpenCodeConfig.fromEnv()
        val client = openCodeHttpClient()
        try {
            val generator = CitizenSummaryGenerator(
                hybridSearch = HybridSearch(FakeEmbeddingProvider, FakeFragmentRepository),
                textGenerationProvider = OpenCodeTextGenerationProvider(client, config),
            )
            val result = generator.generate(publication())
            val valid = assertIs<CitizenSummaryResult.Valid>(result)
            assertEquals(URL, valid.summary.fuenteOficial)
            assertTrue(valid.summary.avisoIA)
            println(
                "CitizenSummaryGeneratorLiveTest: queCambia='${valid.summary.queCambia}' " +
                    "aQuienAfecta='${valid.summary.aQuienAfecta}' " +
                    "cifras=${valid.summary.cifrasClave.size}"
            )
        } finally {
            client.close()
        }
    }

    private fun publication(): Publicacion = Publicacion(
        id = "BOE-A-2026-1",
        titulo = "Real Decreto de prueba sobre el régimen de ayudas",
        fechaPublicacion = "2026-10-10",
        organismo = "MINISTERIO DE PRUEBA",
        seccion = SeccionBoeDto.I,
        epigrafe = "Disposiciones generales",
        texto = CONTEXT,
        urlOficial = URL,
        urlXml = null,
        urlPdf = null,
        rango = "Real Decreto",
        categoria = CategoriaDto.NORMAS_Y_LEGISLACION,
    )

    /** Proveedor de embeddings simulado: la recuperación no usa el modelo real. */
    private object FakeEmbeddingProvider : EmbeddingProvider {
        override val dimensions: Int = 3

        override fun embedQuery(text: String): FloatArray = floatArrayOf(1f, 0f, 0f)

        override fun embedPassage(text: String): FloatArray = floatArrayOf(1f, 0f, 0f)

        override fun close() = Unit
    }

    /** Repositorio simulado que devuelve un fragmento de prueba en memoria. */
    private object FakeFragmentRepository : FragmentRepository {
        private val matches = listOf(
            FragmentMatch(
                fragment = FragmentEntity(
                    publicationId = "BOE-A-2026-1",
                    order = 0,
                    reference = "Artículo 1",
                    content = CONTEXT,
                ),
                distance = 0.0,
            )
        )

        override fun save(fragment: FragmentEntity): FragmentEntity = fragment

        override fun listByPublication(publicationId: String): List<FragmentEntity> = emptyList()

        override fun saveEmbedding(publicationId: String, order: Int, embedding: FloatArray): Boolean = true

        override fun findNearest(queryEmbedding: FloatArray, limit: Int): List<FragmentMatch> =
            findNearest(queryEmbedding, SearchFilter(), limit)

        override fun findNearest(
            queryEmbedding: FloatArray,
            filter: SearchFilter,
            limit: Int,
        ): List<FragmentMatch> = matches

        override fun findEmbedding(publicationId: String, order: Int): FloatArray? = null
    }

    private companion object {
        const val URL = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1"

        const val CONTEXT =
            "Artículo 1. Objeto. Este real decreto regula el régimen de ayudas para la " +
                "realización de prácticas formativas, con una cuantía máxima de 1.000 euros " +
                "por beneficiario y un plazo de solicitud de 30 días hábiles desde su publicación."
    }
}
