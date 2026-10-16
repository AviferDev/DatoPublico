package es.aviferdev.datopublico.backend.rag.chat

/**
 * Puerto **interno** de resolución de la fuente oficial de un fragmento citado
 * (FT00025).
 *
 * Es un `fun interface` (costura inyectable) que cierra el encaje entre el índice
 * de fragmento que aporta el modelo y la **cita a la fuente oficial**: el servicio
 * de chat **no** conoce `PublicationRepository`, JDBC ni Ktor. En **producción**
 * (FT00031) se implementa sobre `PublicationRepository.findById`
 * (`Publication.id` → `ChatCitation(id, titulo, urlOficial)`); en el gate se usa un
 * doble determinista.
 *
 * @see RagChatService
 */
fun interface CitationResolver {
    /**
     * Resuelve la **fuente oficial** de la publicación [publicationId].
     *
     * @param publicationId identificador oficial de la publicación del BOE.
     * @return la cita con la URL oficial, o `null` si la publicación no se puede
     *   resolver (no se inventa una fuente).
     */
    fun resolve(publicationId: String): ChatCitation?
}
