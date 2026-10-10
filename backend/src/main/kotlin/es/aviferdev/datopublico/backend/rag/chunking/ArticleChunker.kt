package es.aviferdev.datopublico.backend.rag.chunking

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.rag.Fragment

/**
 * Fragmentador **puro, determinista y sin red/BD** del texto de una
 * [Publicacion].
 *
 * Divide [Publicacion.texto] (ya normalizado por el parser del BOE) en
 * [Fragment] por **artículo/disposición** conservando la **referencia**, con
 * **variante por sección** para las secciones sin artículos (II.A/II.B/IV/V.A/
 * V.B/V.C) y con el texto cubierto **sin solapamientos**. Es la tarea `0.3-T01`
 * y la base de FT00015 (embeddings) y FT00016 (pgvector); **no** generará
 * embeddings, **no** escribe en PostgreSQL y **no** se cablea en el arranque.
 *
 * Garantías:
 * - **Cobertura total**: la concatenación de los `content` reproduce las líneas
 *   no vacías del texto de origen, en orden, sin repetir ni perder líneas; un
 *   único párrafo que supere el presupuesto se parte por palabras.
 * - **Presupuesto**: cada fragmento respeta [maxTokens] según [estimateTokens].
 * - **Referencia**: el encabezado de artículo/disposición, o
 *   [SECTION_REFERENCE_PREFIX] + `seccion.name` en los bloques sin encabezado.
 * - **`order` contiguo** `0..n-1` en orden de documento.
 *
 * No usa IA, red, base de datos, reloj, aleatoriedad ni estado compartido.
 *
 * @param maxTokens presupuesto de tokens por fragmento (default [MAX_TOKENS]);
 *   FT00015 podrá inyectar el contador real del modelo E5 sin cambiar la firma.
 * @param charsPerToken caracteres por token de la estimación provisional
 *   (default [CHARS_PER_TOKEN]); FT00015 podrá recalibrarlo.
 */
class ArticleChunker(
    private val maxTokens: Int = MAX_TOKENS,
    private val charsPerToken: Int = CHARS_PER_TOKEN,
) {

    /**
     * Fragmenta [publicacion] en orden de documento.
     *
     * @param publicacion publicación con su texto ya normalizado
     *   ([Publicacion.texto]); si es `null` o solo espacios devuelve lista vacía
     *   (nunca lanza excepción).
     * @return fragmentos con `order` contiguo desde `0`; lista vacía sin texto.
     */
    fun chunk(publicacion: Publicacion): List<Fragment> {
        val sectionReference = SECTION_REFERENCE_PREFIX + publicacion.seccion.name
        val lines = nonEmptyLines(publicacion.texto)
        val blocks = blocksOf(lines, sectionReference)
        return blocks
            .flatMap { block -> packedContents(block).map { content -> block.reference to content } }
            .filter { (_, content) -> content.isNotBlank() }
            .mapIndexed { index, (reference, content) ->
                Fragment(publicacion.id, index, reference, content)
            }
    }

    /**
     * Estimación **provisional** de tokens de [text]: `ceil(length / charsPerToken)`.
     *
     * Es conservadora para español y **sustituible**: FT00015 inyectará el
     * tokenizador real de `multilingual-e5-small` por el constructor. Se expone
     * para que FT00015 y los tests puedan comprobar el presupuesto sin duplicar
     * la fórmula.
     */
    fun estimateTokens(text: String): Int = (text.length + charsPerToken - 1) / charsPerToken

    /** Líneas no vacías del texto de origen, recortadas y en orden. */
    private fun nonEmptyLines(text: String?): List<String> =
        text.orEmpty().lines().map { line -> line.trim() }.filter { line -> line.isNotEmpty() }

    /**
     * Divide [lines] en bloques consecutivos: cada encabezado inicia un bloque y
     * las líneas previas al primero forman el bloque **introductorio**.
     *
     * Si no hay encabezados, hay un único bloque con [sectionReference]: es la
     * variante por sección de II.A/II.B/IV/V.A/V.B/V.C.
     */
    private fun blocksOf(lines: List<String>, sectionReference: String): List<Block> = buildList {
        var current = mutableListOf<String>()
        var currentReference = sectionReference
        lines.forEach { line ->
            if (isHeader(line)) {
                if (current.isNotEmpty()) {
                    add(Block(currentReference, current.toList()))
                }
                current = mutableListOf(line)
                currentReference = line
            } else {
                current.add(line)
            }
        }
        if (current.isNotEmpty()) {
            add(Block(currentReference, current.toList()))
        }
    }

    /** ¿La [line] es un encabezado de artículo o disposición (anclado al inicio)? */
    private fun isHeader(line: String): Boolean = HEADER_PATTERN.containsMatchIn(line)

    /**
     * Contenidos de los fragmentos del bloque [block], cada uno dentro del
     * presupuesto y en orden; nunca mezcla dos encabezados.
     */
    private fun packedContents(block: Block): List<String> {
        val fragments = mutableListOf<String>()
        val current = mutableListOf<String>()
        block.lines.forEach { line ->
            val candidate = (current + line).joinToString(NEWLINE)
            if (estimateTokens(candidate) <= maxTokens) {
                current.add(line)
            } else {
                flush(current, fragments)
                appendLine(line, current, fragments)
            }
        }
        flush(current, fragments)
        return fragments
    }

    /** Cierra el fragmento [current] (si tiene líneas) y lo reinicia. */
    private fun flush(current: MutableList<String>, fragments: MutableList<String>) {
        if (current.isNotEmpty()) {
            fragments.add(current.joinToString(NEWLINE))
            current.clear()
        }
    }

    /** Añade [line] al fragmento [current] o la parte por palabras si no cabe. */
    private fun appendLine(line: String, current: MutableList<String>, fragments: MutableList<String>) {
        if (estimateTokens(line) <= maxTokens) {
            current.add(line)
        } else {
            val parts = splitLongLine(line)
            fragments.addAll(parts.dropLast(1))
            current.add(parts.last())
        }
    }

    /**
     * Parte [line] por palabras en trozos consecutivos que caben en el
     * presupuesto.
     *
     * Conserva el orden y no repite ni pierde palabras; una palabra mayor que el
     * presupuesto (p. ej. una URL muy larga) se trocea por caracteres.
     */
    private fun splitLongLine(line: String): List<String> {
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        line.split(WORD_SEPARATOR).forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (estimateTokens(candidate) <= maxTokens) {
                current = StringBuilder(candidate)
            } else {
                appendPart(current, parts)
                appendWord(word, current, parts)
            }
        }
        appendPart(current, parts)
        return parts
    }

    /** Envía el fragmento en construcción [current] a [parts] (si no está vacío). */
    private fun appendPart(current: StringBuilder, parts: MutableList<String>) {
        if (current.isNotEmpty()) {
            parts.add(current.toString())
            current.setLength(0)
        }
    }

    /** Añade [word] al fragmento [current] o la trocea por caracteres si no cabe. */
    private fun appendWord(word: String, current: StringBuilder, parts: MutableList<String>) {
        if (estimateTokens(word) <= maxTokens) {
            current.append(word)
        } else {
            val wordParts = word.chunked(maxTokens * charsPerToken)
            parts.addAll(wordParts.dropLast(1))
            current.append(wordParts.last())
        }
    }

    /** Bloque intermedio: una referencia y sus líneas, aún sin empaquetar. */
    private data class Block(val reference: String, val lines: List<String>)

    private companion object {
        /** Separador de líneas de los fragmentos (el texto de origen usa `\n`). */
        const val NEWLINE = "\n"

        /** Prefijo de la referencia de sección (variante sin artículos). */
        const val SECTION_REFERENCE_PREFIX = "Sección "

        /** Presupuesto de tokens por fragmento para el modelo E5 (contexto 512). */
        const val MAX_TOKENS = 512

        /**
         * Caracteres por token de la estimación provisional (conservadora para
         * español). A calibrar con el tokenizador real en FT00015.
         */
        const val CHARS_PER_TOKEN = 3

        /** Encabezado de artículo/disposición anclado a **inicio de línea**. */
        val HEADER_PATTERN = Regex("^(Artículo|Disposición)\\b")

        /** Separador de palabras de un párrafo para partirlo por presupuesto. */
        val WORD_SEPARATOR = Regex("\\s+")
    }
}
