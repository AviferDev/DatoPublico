package es.aviferdev.datopublico.backend.rag.guardrails

import java.util.Locale

/**
 * Validez **estructural** de la fuente oficial de un resumen (FT00022).
 *
 * Es un `object` **puro** (sin red, BD, reloj ni estado): solo comprueba que la
 * fuente sea un enlace `https://` cuyo **host** pertenezca al BOE y que sea
 * **idéntica** al `urlOficial` de la publicación de origen. La igualdad garantiza
 * la **trazabilidad** (en FT00020 la fuente se inyecta desde la publicación); la
 * lista blanca garantiza que apunta al BOE.
 *
 * **No** comprueba **alcanzabilidad**: eso es red y vive en el puerto
 * [SourceReachability].
 */
object OfficialSourceValidator {

    /** Hosts oficiales del BOE admitidos (con y sin `www`). */
    private val OFFICIAL_HOSTS = setOf("www.boe.es", "boe.es")

    /** Esquema obligatorio de la fuente oficial. */
    private const val HTTPS_SCHEME = "https://"

    /**
     * Comprueba que [source] es una fuente oficial válida de [expected].
     *
     * @param source fuente declarada por el resumen (`SummaryDto.fuenteOficial`).
     * @param expected URL oficial de la publicación (`Publicacion.urlOficial`).
     * @return `true` solo si [source] recortada empieza por `https://`, su host es
     *   del BOE y es exactamente igual a [expected].
     */
    fun isOfficial(source: String, expected: String): Boolean {
        val trimmed = source.trim()
        return trimmed.startsWith(HTTPS_SCHEME) &&
            hostOf(trimmed) in OFFICIAL_HOSTS &&
            trimmed == expected
    }

    /**
     * Extrae el **host** de una URL `https://…` sin usar `java.net.URI` (que puede
     * lanzar con entradas malformadas): descarta el esquema, el *userinfo*, la
     * ruta, la consulta, el fragmento y el puerto.
     *
     * @param url URL absoluta ya recortada y con esquema `https://`.
     * @return host en minúsculas (`Locale.ROOT`); cadena vacía si no hay host.
     */
    private fun hostOf(url: String): String {
        val authority = url.removePrefix(HTTPS_SCHEME)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
        return authority.substringBefore(':').lowercase(Locale.ROOT)
    }
}
