package es.aviferdev.datopublico.app

/** Caracteres que no necesitan codificación en un segmento de ruta (RFC 3986). */
private const val UNRESERVED_CHARACTERS: String =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

/** Prefijo literal de la ruta de detalle, delante del `id` ya codificado. */
private const val DETAIL_PREFIX: String = "detail/"

/**
 * Rutas del grafo de navegación del cliente.
 *
 * Se usan rutas **string** con `navArgument` (y no serialización tipada) para no
 * añadir el plugin de `kotlinx-serialization` a `:composeApp`. Los
 * identificadores van en inglés; el KDoc, en castellano.
 */
object AppRoutes {
    /** Ruta del feed diario; es el destino inicial del grafo. */
    const val FEED: String = "feed"

    /** Patrón de la ruta de detalle, con el argumento entre llaves. */
    const val DETAIL: String = "detail/{publicationId}"

    /** Clave del argumento `publicationId` de la ruta de detalle. */
    const val ARG_PUBLICATION_ID: String = "publicationId"

    /**
     * Construye la ruta de detalle para una publicación.
     *
     * El `id` se codifica por si trae caracteres reservados; los identificadores
     * del BOE (`BOE-A-aaaa-nnnnn`) son URL-safe y pasan sin cambios.
     *
     * @param publicationId identificador oficial de la publicación.
     * @return ruta lista para `navController.navigate(…)`.
     */
    fun detail(publicationId: String): String = DETAIL_PREFIX + encodePathSegment(publicationId)
}

/**
 * Codifica un valor como **segmento de ruta** percent-encoded (UTF-8).
 *
 * Es una función **pura** y multiplataforma (no usa `java.net.URLEncoder`): deja
 * intactos los caracteres no reservados y codifica el resto byte a byte.
 *
 * @param value valor a codificar.
 * @return el valor con los caracteres reservados codificados.
 */
private fun encodePathSegment(value: String): String {
    val builder = StringBuilder()
    value.encodeToByteArray().forEach { byte ->
        val unsigned = byte.toInt() and 0xFF
        val character = unsigned.toChar()
        if (UNRESERVED_CHARACTERS.contains(character)) {
            builder.append(character)
        } else {
            builder.append('%').append(unsigned.toString(16).uppercase().padStart(2, '0'))
        }
    }
    return builder.toString()
}
