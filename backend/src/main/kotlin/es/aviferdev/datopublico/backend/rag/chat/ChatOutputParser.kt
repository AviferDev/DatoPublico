package es.aviferdev.datopublico.backend.rag.chat

import es.aviferdev.datopublico.serialization.DatoPublicoJson
import java.util.Locale

/**
 * Parser **puro** de la salida del modelo al DTO [ChatOutputDto] y del token de
 * estado al enum interno [ChatStatus].
 *
 * Es determinista y sin red/BD/estado: recorta espacios, elimina las *fences* de
 * Markdown (```` ```json … ``` ````) y parsea con [DatoPublicoJson]. Una salida
 * vacía o un JSON inválido lanza [ChatGenerationException]; un estado desconocido
 * es **fail-fast** (no se inventa un estado). No repara ni reintenta: eso queda
 * fuera del alcance de FT00025.
 */
class ChatOutputParser {

    /**
     * Interpreta [raw] como la salida JSON del modelo.
     *
     * @param raw texto crudo devuelto por el proveedor.
     * @return el DTO de salida del chat.
     * @throws ChatGenerationException si [raw] está vacío o no es un JSON válido
     *   de [ChatOutputDto].
     */
    fun parse(raw: String): ChatOutputDto {
        val json = stripFences(raw).trim()
        if (json.isEmpty()) {
            throw ChatGenerationException("La respuesta del proveedor está vacía.")
        }
        return decode(json)
    }

    /**
     * Traduce el token de estado del wire a [ChatStatus].
     *
     * Se toleran espacios y mayúsculas (`"Respondida"`), pero un token que no sea
     * uno de los tres del dominio es **fail-fast** (sin `else if`).
     *
     * @param token token de estado devuelto por el modelo.
     * @return el estado interno correspondiente.
     * @throws ChatGenerationException si [token] no es un estado conocido.
     */
    fun statusOf(token: String): ChatStatus = when (token.trim().lowercase(Locale.ROOT)) {
        STATUS_ANSWERED -> ChatStatus.ANSWERED
        STATUS_REFUTED -> ChatStatus.REFUTED
        STATUS_UNFOUNDED -> ChatStatus.UNFOUNDED
        else -> throw ChatGenerationException("Estado desconocido en la respuesta del chat: '$token'.")
    }

    /** Parsea [json] con el formato compartido; un fallo es error tipado. */
    private fun decode(json: String): ChatOutputDto = try {
        DatoPublicoJson.decodeFromString<ChatOutputDto>(json)
    } catch (error: Exception) {
        throw ChatGenerationException("La respuesta del proveedor no es un JSON válido.", error)
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
        /** Token de wire del estado «respondida». */
        const val STATUS_ANSWERED = "respondida"

        /** Token de wire del estado «refutada». */
        const val STATUS_REFUTED = "refutada"

        /** Token de wire del estado «sin_fundamento». */
        const val STATUS_UNFOUNDED = "sin_fundamento"

        /** Valla de Markdown. */
        const val FENCE = "```"

        /** Etiqueta de lenguaje que puede seguir a la valla de apertura. */
        const val LANGUAGE_TAG = "json"
    }
}
