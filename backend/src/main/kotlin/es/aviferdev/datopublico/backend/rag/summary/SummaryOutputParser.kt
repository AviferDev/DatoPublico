package es.aviferdev.datopublico.backend.rag.summary

import es.aviferdev.datopublico.serialization.DatoPublicoJson

/**
 * Parser **puro** de la salida del modelo al DTO [SummaryGenerationDto].
 *
 * Es determinista y sin red/BD/estado: recorta espacios, elimina las *fences* de
 * Markdown (```` ```json … ``` ````) y parsea con [DatoPublicoJson]. Una salida
 * vacía o un JSON inválido lanza [SummaryGenerationException] (no se devuelve un
 * resumen a medias). No repara ni reintenta: eso queda fuera del alcance de
 * FT00020.
 */
class SummaryOutputParser {

    /**
     * Interpreta [raw] como la salida JSON del modelo.
     *
     * @param raw texto crudo devuelto por el proveedor.
     * @return el DTO de salida del resumen.
     * @throws SummaryGenerationException si [raw] está vacío o no es un JSON
     *   válido de [SummaryGenerationDto].
     */
    fun parse(raw: String): SummaryGenerationDto {
        val json = stripFences(raw).trim()
        if (json.isEmpty()) {
            throw SummaryGenerationException("La respuesta del proveedor está vacía.")
        }
        return decode(json)
    }

    /** Parsea [json] con el formato compartido; un fallo es error tipado. */
    private fun decode(json: String): SummaryGenerationDto = try {
        DatoPublicoJson.decodeFromString<SummaryGenerationDto>(json)
    } catch (error: Exception) {
        throw SummaryGenerationException("La respuesta del proveedor no es un JSON válido.", error)
    }

    /**
     * Quita las *fences* de Markdown que envuelvan el JSON y la etiqueta `json`.
     *
     * Si no hay vallas devuelve el texto tal cual (ya recortado).
     */
    private fun stripFences(raw: String): String {
        val trimmed = raw.trim()
        val opening = trimmed.indexOf(FENCE)
        val closing = trimmed.lastIndexOf(FENCE)
        return if (opening >= 0 && closing > opening) {
            trimmed.substring(opening + FENCE.length, closing).removePrefix(LANGUAGE_TAG)
        } else {
            trimmed
        }
    }

    private companion object {
        /** Valla de Markdown. */
        const val FENCE = "```"

        /** Etiqueta de lenguaje que puede seguir a la valla de apertura. */
        const val LANGUAGE_TAG = "json"
    }
}
