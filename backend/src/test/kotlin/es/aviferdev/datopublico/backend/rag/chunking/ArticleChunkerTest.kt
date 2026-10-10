package es.aviferdev.datopublico.backend.rag.chunking

import es.aviferdev.datopublico.backend.ingesta.publicacion.BoeXmlParser
import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.persistence.toEntity
import es.aviferdev.datopublico.backend.rag.Fragment
import es.aviferdev.datopublico.model.SeccionBoeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fragmentador por artículo/disposición (FT00014), **sin red ni base de datos**.
 *
 * Cubre los escenarios 1–7 del spec con los **fixtures reales** del BOE
 * (`backend/src/test/resources/boe`): fragmentación por encabezado conservando la
 * referencia, cobertura sin solapamiento, variante por sección, artículo largo,
 * determinismo y `order` contiguo, texto nulo/vacío y mapeo a [FragmentEntity]
 * (sin abrir conexión). No se importa nada de `infra/Database*` ni de `java.sql`.
 */
class ArticleChunkerTest {

    private val parser = BoeXmlParser()
    private val chunker = ArticleChunker()

    @Test
    fun `fragments a normative document by article header keeping the reference`() {
        val publication = publicationFromFixture("texto-I-2026-20979", SeccionBoeDto.I)
        val fragments = chunker.chunk(publication)
        val headerLines = nonEmptyLines(publication.texto).filter { line -> HEADER_REGEX.containsMatchIn(line) }

        assertEquals(20, headerLines.size)
        assertEquals(headerLines, referencedHeaders(fragments))
        assertEquals(SECTION_REFERENCE_I, fragments.first().reference)
        assertTrue(fragments.all { fragment -> !fragment.content.isBlank() })
        fragments
            .filter { fragment -> fragment.reference in headerLines }
            .groupBy { fragment -> fragment.reference }
            .forEach { (reference, group) ->
                val header = checkNotNull(reference)
                assertTrue(group.first().content.startsWith(header), group.first().content)
            }
    }

    @Test
    fun `concatenated fragments reproduce the source lines without duplicates or loss`() {
        val publication = publicationFromFixture("texto-VB-2024-76", SeccionBoeDto.V_B)
        val source = nonEmptyLines(publication.texto).joinToString(NEWLINE)
        val joined = chunker.chunk(publication).joinToString(NEWLINE) { fragment -> fragment.content }

        assertEquals(source, joined)
    }

    @Test
    fun `splitting a long paragraph preserves every word in order`() {
        val publication = publicationFromFixture("texto-I-2026-20979", SeccionBoeDto.I)
        val fragments = chunker.chunk(publication)

        assertEquals(
            words(nonEmptyLines(publication.texto).joinToString(NEWLINE)),
            words(fragments.joinToString(NEWLINE) { fragment -> fragment.content }),
        )
    }

    @Test
    fun `a section without articles groups paragraphs under the section reference`() {
        val publication = publicationFromFixture("texto-VB-2024-76", SeccionBoeDto.V_B)
        val fragments = chunker.chunk(publication)

        assertTrue(fragments.size > 1)
        assertTrue(fragments.all { fragment -> fragment.reference == SECTION_REFERENCE_V_B })
        assertTrue(fragments.none { fragment -> HEADER_REGEX.containsMatchIn(fragment.reference.orEmpty()) })
        assertTrue(fragments.all { fragment -> chunker.estimateTokens(fragment.content) <= MAX_TOKENS })
    }

    @Test
    fun `an article longer than the budget splits with the same reference`() {
        val body = (1..800).joinToString(" ") { index -> "palabra$index" }
        val publication = publication(
            id = "BOE-TEST-LONG",
            section = SeccionBoeDto.I,
            text = "$LONG_ARTICLE_HEADER\n$body",
        )
        val fragments = chunker.chunk(publication)

        assertTrue(fragments.size > 1)
        assertTrue(fragments.all { fragment -> fragment.reference == LONG_ARTICLE_HEADER })
        assertTrue(fragments.all { fragment -> chunker.estimateTokens(fragment.content) <= MAX_TOKENS })
        assertEquals(
            words("$LONG_ARTICLE_HEADER $body"),
            words(fragments.joinToString(" ") { fragment -> fragment.content }),
        )
    }

    @Test
    fun `order is contiguous and chunking is deterministic`() {
        val publication = publicationFromFixture("texto-I-2026-20979", SeccionBoeDto.I)
        val fragments = chunker.chunk(publication)

        assertEquals(fragments.indices.toList(), fragments.map { fragment -> fragment.order })
        assertEquals(fragments, chunker.chunk(publication))
    }

    @Test
    fun `null or blank text returns an empty list`() {
        val withoutText = publicationFromFixture("texto-sin-texto", SeccionBoeDto.IV)

        assertTrue(chunker.chunk(withoutText).isEmpty())
        assertTrue(chunker.chunk(publication(id = "BOE-TEST-NULL", text = null)).isEmpty())
        assertTrue(chunker.chunk(publication(id = "BOE-TEST-EMPTY", text = "")).isEmpty())
        assertTrue(chunker.chunk(publication(id = "BOE-TEST-BLANK", text = "  \n\n\t ")).isEmpty())
    }

    @Test
    fun `domain fragments map to persistence entities without an id`() {
        val fragments = chunker.chunk(publicationFromFixture("texto-VC-2024-92", SeccionBoeDto.V_C))
        val entities = fragments.map { fragment -> fragment.toEntity() }

        assertEquals(fragments.size, entities.size)
        fragments.zip(entities).forEach { (fragment, entity) ->
            assertNull(entity.id)
            assertEquals(fragment.publicationId, entity.publicationId)
            assertEquals(fragment.order, entity.order)
            assertEquals(fragment.reference, entity.reference)
            assertEquals(fragment.content, entity.content)
        }
    }

    @Test
    fun `every fragment of every real fixture respects the budget and is not blank`() {
        FIXTURES.forEach { (name, section) ->
            val fragments = chunker.chunk(publicationFromFixture(name, section))

            assertTrue(fragments.isNotEmpty(), name)
            assertEquals(fragments.indices.toList(), fragments.map { fragment -> fragment.order }, name)
            assertTrue(
                fragments.all { fragment -> chunker.estimateTokens(fragment.content) <= MAX_TOKENS },
                name,
            )
            assertTrue(fragments.all { fragment -> !fragment.content.isBlank() }, name)
        }
    }

    /** Encabezados distintos referenciados por [fragments], en orden de documento. */
    private fun referencedHeaders(fragments: List<Fragment>): List<String> =
        fragments
            .mapNotNull { fragment -> fragment.reference }
            .filter { reference -> HEADER_REGEX.containsMatchIn(reference) }
            .distinct()

    /** Construye la [Publicacion] de un fixture real parseado, con su sección. */
    private fun publicationFromFixture(name: String, section: SeccionBoeDto): Publicacion =
        publication(
            id = "BOE-TEST-$name",
            section = section,
            text = parser.parse(resource("$name.xml")).texto,
        )

    /** Publicación de prueba en memoria (sin red): señales neutras salvo [text]. */
    private fun publication(
        id: String,
        section: SeccionBoeDto = SeccionBoeDto.I,
        text: String? = null,
    ): Publicacion = Publicacion(
        id = id,
        titulo = "Publicación $id",
        fechaPublicacion = PUBLISHED_DATE,
        organismo = null,
        seccion = section,
        epigrafe = null,
        texto = text,
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=$id",
        urlXml = null,
        urlPdf = null,
        rango = null,
    )

    /** Líneas no vacías del texto de origen (misma vista que consume el chunker). */
    private fun nonEmptyLines(text: String?): List<String> =
        text.orEmpty().lines().map { line -> line.trim() }.filter { line -> line.isNotEmpty() }

    /** Palabras de [text] ignorando los espacios (cobertura sin pérdida). */
    private fun words(text: String): List<String> =
        text.split(WORD_SEPARATOR).filter { word -> word.isNotEmpty() }

    /** Lee un recurso de `src/test/resources/boe` como texto UTF-8. */
    private fun resource(path: String): String =
        checkNotNull(javaClass.getResourceAsStream("/boe/$path")) { "No se encontró /boe/$path" }
            .readBytes()
            .decodeToString()

    private companion object {
        /** Fecha de publicación de las publicaciones de prueba. */
        const val PUBLISHED_DATE = "2026-10-09"

        /** Presupuesto de tokens por fragmento (copia del contrato de FT00014). */
        const val MAX_TOKENS = 512

        /** Referencia de sección del bloque introductorio de la Sección I. */
        const val SECTION_REFERENCE_I = "Sección I"

        /** Referencia de sección de una sección irregular (sin artículos). */
        const val SECTION_REFERENCE_V_B = "Sección V_B"

        /** Encabezado sintético del artículo largo de prueba. */
        const val LONG_ARTICLE_HEADER = "Artículo 1. Norma extensa."

        /** Separador de líneas (el texto normalizado del BOE usa `\n`). */
        const val NEWLINE = "\n"

        /** Encabezado de artículo/disposición anclado a inicio de línea. */
        val HEADER_REGEX = Regex("^(Artículo|Disposición)\\b")

        /** Separador de palabras para comprobar la cobertura. */
        val WORD_SEPARATOR = Regex("\\s+")

        /** Fixtures reales con texto y su sección (los 8 del sumario). */
        val FIXTURES = listOf(
            "texto-I-2026-20979" to SeccionBoeDto.I,
            "texto-IIA-2024-87" to SeccionBoeDto.II_A,
            "texto-IIB-2024-93" to SeccionBoeDto.II_B,
            "texto-III-2024-117" to SeccionBoeDto.III,
            "texto-IV-2024-1" to SeccionBoeDto.IV,
            "texto-VA-2024-25" to SeccionBoeDto.V_A,
            "texto-VB-2024-76" to SeccionBoeDto.V_B,
            "texto-VC-2024-92" to SeccionBoeDto.V_C,
        )
    }
}
