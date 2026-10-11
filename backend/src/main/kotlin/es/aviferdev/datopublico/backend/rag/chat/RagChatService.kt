package es.aviferdev.datopublico.backend.rag.chat

import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.rag.generation.TextGenerationProvider
import es.aviferdev.datopublico.backend.rag.generation.TokenUsage
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter

/**
 * Servicio de **generación del chat RAG** (FT00025): dada una pregunta o una
 * afirmación, recupera el contexto, construye el prompt, invoca el proveedor de
 * texto, parsea la salida estructurada (**estado + respuesta + citas**), verifica
 * el **grounding** contra el contexto y **inyecta la fuente oficial** de cada cita.
 *
 * Es una **librería sin wiring** (no se construye en `Application.kt` ni en el
 * job): compone [HybridSearch] (recuperación, FT00017), un [TextGenerationProvider]
 * intercambiable (OpenCode en producción; un doble en los tests), el
 * [ChatPromptBuilder], el [ChatOutputParser] y el [CitationResolver]. **No** conoce
 * Ktor, HTTP, JDBC ni el modelo concreto.
 *
 * Garantías anti-alucinación:
 * - La respuesta se fundamenta **solo** en el contexto recuperado; una respuesta
 *   `respondida`/`refutada` no sustentada por el grounding **degrada** a
 *   [ChatResult.Unfounded] sin publicar la afirmación.
 * - El **contexto vacío** declara [ChatResult.Unfounded] **sin invocar** al
 *   proveedor.
 * - La **fuente oficial** la inyecta el [CitationResolver] desde la publicación; el
 *   modelo solo aporta **índices** de fragmento. Una respuesta citada exige al
 *   menos una cita resoluble.
 *
 * @param hybridSearch recuperación híbrida que aporta los fragmentos de contexto.
 * @param textGenerationProvider proveedor de texto (sustituible por un doble).
 * @param citationResolver puerto que resuelve la fuente oficial de una publicación.
 * @param promptBuilder constructor puro del prompt.
 * @param parser parser puro de la salida del modelo.
 */
class RagChatService(
    private val hybridSearch: HybridSearch,
    private val textGenerationProvider: TextGenerationProvider,
    private val citationResolver: CitationResolver,
    private val promptBuilder: ChatPromptBuilder = ChatPromptBuilder(),
    private val parser: ChatOutputParser = ChatOutputParser(),
) {

    /**
     * Responde [query] (pregunta o afirmación) fundamentándose en el corpus.
     *
     * @param query pregunta o afirmación en lenguaje natural (no puede estar en
     *   blanco).
     * @param filter filtro de metadatos opcional de la recuperación; por defecto,
     *   sin filtros (similitud pura).
     * @param limit número máximo de fragmentos de contexto (`>= 1`).
     * @return [ChatResult.Answered]/[ChatResult.Refuted] con sus citas o
     *   [ChatResult.Unfounded] si el corpus no permite responder.
     * @throws IllegalArgumentException si [query] está en blanco o `limit < 1`
     *   (fail-fast **antes** de recuperar o invocar al proveedor).
     * @throws es.aviferdev.datopublico.backend.rag.generation.TextGenerationException
     *   si el proveedor falla.
     * @throws ChatGenerationException si la salida no es interpretable, el estado
     *   es desconocido o el modelo citó un índice fuera de rango.
     */
    suspend fun answer(
        query: String,
        filter: SearchFilter = SearchFilter(),
        limit: Int = DEFAULT_LIMIT,
    ): ChatResult {
        require(query.isNotBlank()) { "La consulta del chat no puede estar en blanco." }
        require(limit >= 1) { "El límite de fragmentos debe ser >= 1, pero es $limit." }
        val fragments = hybridSearch.search(query, filter, limit).map { match -> match.fragment }
        return if (fragments.isEmpty()) {
            ChatResult.Unfounded(UNFOUNDED_MESSAGE)
        } else {
            generate(query, fragments)
        }
    }

    /** Construye el prompt, invoca al proveedor, parsea la salida y traduce el resultado. */
    private suspend fun generate(query: String, fragments: List<FragmentEntity>): ChatResult {
        val request = promptBuilder.build(query, fragments)
        val response = textGenerationProvider.generate(request)
        val output = parser.parse(response.text)
        return resultOf(parser.statusOf(output.status), output, fragments, response.usage)
    }

    /** Traduce el estado de dominio al resultado tipado correspondiente. */
    private fun resultOf(
        status: ChatStatus,
        output: ChatOutputDto,
        fragments: List<FragmentEntity>,
        usage: TokenUsage?,
    ): ChatResult = when (status) {
        ChatStatus.UNFOUNDED -> ChatResult.Unfounded(output.answer, usage)
        ChatStatus.ANSWERED -> citedResult(output, fragments, usage, ChatStatus.ANSWERED)
        ChatStatus.REFUTED -> citedResult(output, fragments, usage, ChatStatus.REFUTED)
    }

    /**
     * Verifica el grounding de una respuesta citada: si no está sustentada por el
     * contexto, **degrada** a [ChatResult.Unfounded] con el mensaje de dominio (no
     * se publica una afirmación fuera del corpus).
     */
    private fun citedResult(
        output: ChatOutputDto,
        fragments: List<FragmentEntity>,
        usage: TokenUsage?,
        status: ChatStatus,
    ): ChatResult = if (!ChatGroundingChecker.isSupported(output.answer, fragments)) {
        ChatResult.Unfounded(UNFOUNDED_MESSAGE, usage)
    } else {
        resultWithCitations(output.answer, citationsOf(output.citations, fragments), usage, status)
    }

    /**
     * Construye el resultado citado; una respuesta `respondida`/`refutada` **exige**
     * al menos una cita resoluble: sin ninguna, degrada a [ChatResult.Unfounded].
     */
    private fun resultWithCitations(
        answer: String,
        citations: List<ChatCitation>,
        usage: TokenUsage?,
        status: ChatStatus,
    ): ChatResult = when {
        citations.isEmpty() -> ChatResult.Unfounded(UNFOUNDED_MESSAGE, usage)
        status == ChatStatus.ANSWERED -> ChatResult.Answered(answer, citations, usage)
        else -> ChatResult.Refuted(answer, citations, usage)
    }

    /**
     * Resuelve los índices citados a citas a la fuente oficial, **deduplicadas**
     * por `publicationId` y descartando las que no se pueden resolver.
     *
     * @throws ChatGenerationException si algún índice está fuera de rango.
     */
    private fun citationsOf(indices: List<Int>, fragments: List<FragmentEntity>): List<ChatCitation> {
        val publicationIds = indices.map { index -> fragmentAt(index, fragments).publicationId }.distinct()
        return publicationIds.mapNotNull { publicationId -> citationResolver.resolve(publicationId) }
    }

    /** Fragmento citado por [index]; un índice fuera de rango es error tipado. */
    private fun fragmentAt(index: Int, fragments: List<FragmentEntity>): FragmentEntity =
        fragments.getOrNull(index)
            ?: throw ChatGenerationException("El modelo citó un fragmento fuera de rango: índice $index.")

    companion object {
        /**
         * Mensaje de **dominio** para `sin_fundamento` (no es texto de UI; la UI de
         * chat puede sustituirlo por un recurso, FT00037).
         */
        const val UNFOUNDED_MESSAGE =
            "El corpus del BOE no permite responder a esta consulta con fundamento. " +
                "No se ha encontrado información que la sustente."

        /** Número de fragmentos de contexto por defecto. */
        const val DEFAULT_LIMIT = 5
    }
}
