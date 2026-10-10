package es.aviferdev.datopublico.backend.rag.evaluation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests de gate (sin red ni BD) de [RetrievalGoldenSetLoader] (escenarios 2 y 3
 * del spec): el recurso versionado real parsea y es coherente, y un dataset
 * inválido falla con `IllegalArgumentException` **sin** abrir conexión.
 */
class RetrievalGoldenSetTest {

    @Test
    fun `the versioned golden set parses and is coherent`() {
        val dataset = RetrievalGoldenSetLoader.load()
        val corpusIds = dataset.corpus.map { publication -> publication.id }.toSet()
        val relevant = dataset.queries.flatMap { query -> query.relevant }

        assertTrue(dataset.version.isNotBlank())
        assertTrue(dataset.k >= 1)
        assertTrue(dataset.corpus.isNotEmpty())
        assertTrue(dataset.queries.isNotEmpty())
        assertEquals(corpusIds.size, dataset.corpus.size, "los ids del corpus deben ser únicos")
        assertEquals(
            dataset.queries.map { query -> query.id }.toSet().size,
            dataset.queries.size,
            "los ids de las consultas deben ser únicos",
        )
        assertTrue(relevant.isNotEmpty(), "cada consulta debe declarar al menos un relevante")
        assertTrue(
            relevant.all { ref -> ref.publicationId in corpusIds && ref.order >= 0 },
            "todo fragmento relevante debe existir en el corpus",
        )
    }

    @Test
    fun `an empty corpus fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            RetrievalGoldenSetLoader.parse(datasetJson(corpus = ""))
        }
    }

    @Test
    fun `an empty query list fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            RetrievalGoldenSetLoader.parse(datasetJson(queries = ""))
        }
    }

    @Test
    fun `a non positive k fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            RetrievalGoldenSetLoader.parse(datasetJson(k = 0))
        }
    }

    @Test
    fun `duplicate corpus identifiers fail fast`() {
        assertFailsWith<IllegalArgumentException> {
            RetrievalGoldenSetLoader.parse(datasetJson(corpus = "$CORPUS_ITEM, $CORPUS_ITEM"))
        }
    }

    @Test
    fun `duplicate query identifiers fail fast`() {
        assertFailsWith<IllegalArgumentException> {
            RetrievalGoldenSetLoader.parse(datasetJson(queries = "$QUERY_ITEM, $QUERY_ITEM"))
        }
    }

    @Test
    fun `a relevant fragment missing from the corpus fails fast`() {
        val json = datasetJson(queries = QUERY_ITEM.replace("EVAL-1", "EVAL-404"))

        assertFailsWith<IllegalArgumentException> { RetrievalGoldenSetLoader.parse(json) }
    }

    @Test
    fun `a negative relevant order fails fast`() {
        val json = datasetJson(queries = QUERY_ITEM.replace("\"order\": 0", "\"order\": -1"))

        assertFailsWith<IllegalArgumentException> { RetrievalGoldenSetLoader.parse(json) }
    }

    @Test
    fun `a missing dataset file fails fast`() {
        assertFailsWith<IllegalArgumentException> {
            RetrievalGoldenSetLoader.load("/rag/eval/no-existe.json")
        }
    }

    /** Compone un JSON de dataset con las piezas indicadas (para los casos inválidos). */
    private fun datasetJson(
        version: String = "1.0.0",
        k: Int = 3,
        corpus: String = CORPUS_ITEM,
        queries: String = QUERY_ITEM,
    ): String = """{ "version": "$version", "k": $k, "corpus": [$corpus], "queries": [$queries] }"""

    private companion object {
        /** Publicación mínima válida del corpus de prueba. */
        val CORPUS_ITEM = """
            { "id": "EVAL-1", "title": "T", "date": "2026-10-09", "section": "III",
              "category": "becas_subvenciones_y_ayudas", "organization": "Org", "text": "Artículo 1\nTexto." }
        """.trimIndent()

        /** Consulta mínima válida del dataset de prueba. */
        val QUERY_ITEM = """
            { "id": "q-1", "query": "consulta", "filter": { "section": "III" },
              "relevant": [ { "publicationId": "EVAL-1", "order": 0 } ] }
        """.trimIndent()
    }
}
