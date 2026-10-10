package es.aviferdev.datopublico.backend.ingesta.publicacion

/**
 * Cliente del **texto oficial** de una publicación del BOE.
 *
 * Descarga y parsea el XML estructurado indicado por la URL absoluta
 * [obtenerDocumento]. Es una dependencia de red: en el gate y la CI no se usa
 * (los tests doblan el cliente con `MockEngine`); la prueba real es opt-in por
 * `BOE_LIVE_TEST=1`.
 */
interface BoeTextoClient {
    /**
     * Descarga el XML de [urlXml] y lo parsea a [DocumentoBoe].
     *
     * @return el documento parseado, o `null` si la fuente responde `404`
     *   («sin texto disponible» para esa publicación).
     * @throws BoeTextoException si la fuente falla (red), responde un estado
     *   distinto de `200`/`404` o el cuerpo no es XML legible.
     */
    suspend fun obtenerDocumento(urlXml: String): DocumentoBoe?
}

/**
 * Error al obtener o parsear el texto oficial del BOE (red, estado inesperado,
 * XML malformado o intento de DTD/entidades externas).
 *
 * @param message mensaje claro con el identificador/URL y el estado o la causa.
 * @param cause causa original, si la hay.
 */
class BoeTextoException(message: String, cause: Throwable? = null) : Exception(message, cause)
