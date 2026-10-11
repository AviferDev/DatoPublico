package es.aviferdev.datopublico.backend.rag.guardrails

/**
 * Puerto **interno** de alcanzabilidad de la fuente oficial (FT00022).
 *
 * El guardrail ([SummaryGuardrail]) no conoce Ktor: recibe este puerto
 * **opcional** y, si está presente, comprueba que la fuente oficial responde. En
 * producción lo inyectará el wiring (FT00026+); en el gate se sustituye por un
 * doble determinista. Sin sufijo: es un valor interno, no un DTO.
 */
fun interface SourceReachability {
    /**
     * Comprueba si [url] responde de forma alcanzable (2xx/3xx).
     *
     * @param url URL oficial a comprobar.
     * @return `true` si responde 2xx/3xx; `false` si no responde o falla la red.
     */
    suspend fun isReachable(url: String): Boolean
}
