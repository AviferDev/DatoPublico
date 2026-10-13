package es.aviferdev.datopublico.backend.chat

import es.aviferdev.datopublico.backend.persistence.PublicationRepository
import es.aviferdev.datopublico.backend.rag.chat.ChatCitation
import es.aviferdev.datopublico.backend.rag.chat.CitationResolver

/**
 * Primer `CitationResolver` de **producción** del chat RAG (FT00031): resuelve la
 * **fuente oficial** de una publicación a partir de su identificador.
 *
 * Adapta [PublicationRepository.findById] al puerto `fun interface`
 * [CitationResolver] de FT00025: la URL oficial **no** la produce el modelo, se
 * **inyecta** de forma determinista desde `Publicacion.urlOficial` (mismo
 * principio que la fuente del resumen, ADR 0015). Una publicación no resoluble
 * devuelve `null` y el servicio **descarta** esa cita: nunca se inventa una
 * fuente.
 *
 * Vive en `chat/` (no en `persistence/`) para que la dependencia vaya en la
 * dirección natural `chat/ → persistence/`. La lectura JDBC es **bloqueante**:
 * se invoca dentro del `Dispatchers.IO` del servicio, fuera del event loop.
 *
 * @param publicationRepository lectura de la publicación por su `id` (clave
 *   primaria).
 */
class PublicationCitationResolver(
    private val publicationRepository: PublicationRepository,
) : CitationResolver {

    /**
     * Resuelve la cita de la publicación [publicationId].
     *
     * @param publicationId identificador oficial de la publicación del BOE.
     * @return la cita con el título y la URL oficial de la publicación, o `null`
     *   si la publicación no existe (no se inventa una fuente).
     */
    override fun resolve(publicationId: String): ChatCitation? =
        publicationRepository.findById(publicationId)?.let { publication ->
            ChatCitation(
                publicationId = publication.id,
                title = publication.titulo,
                officialUrl = publication.urlOficial,
            )
        }
}
