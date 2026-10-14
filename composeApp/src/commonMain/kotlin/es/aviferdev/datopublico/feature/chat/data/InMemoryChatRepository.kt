package es.aviferdev.datopublico.feature.chat.data

import es.aviferdev.datopublico.feature.chat.domain.ChatAnswer
import es.aviferdev.datopublico.feature.chat.domain.ChatCitation
import es.aviferdev.datopublico.feature.chat.domain.ChatLoadResult
import es.aviferdev.datopublico.feature.chat.domain.ChatStatus
import kotlinx.coroutines.delay

/**
 * Comportamiento del repositorio en memoria; permite ejercitar los estados de la
 * pantalla de chat sin red ni base de datos.
 */
enum class InMemoryChatMode {
    /** Clasifica la consulta con datos de ejemplo deterministas. */
    SAMPLE,

    /** Devuelve un fallo tipado (estado de error con reintento). */
    UNAVAILABLE,
}

/**
 * Implementación **en memoria** de [ChatRepository] con datos de ejemplo.
 *
 * Es un **placeholder** para `@Preview` y tests (el cliente HTTP real es
 * [HttpChatRepository]). En modo [InMemoryChatMode.SAMPLE] clasifica la consulta
 * de forma **determinista** por el texto, **sin** simular la calidad del RAG real
 * (documentado y cubierto por tests): una afirmación con marcadores de refutación
 * ⇒ `REFUTED` con citas; una consulta con palabras del corpus de ejemplo ⇒
 * `ANSWERED` con citas; cualquier otra ⇒ `UNFOUNDED` sin citas.
 *
 * @param mode modo de comportamiento (clasificación de ejemplo o fallo).
 * @param latencyMillis latencia simulada antes de responder; se inyecta **0** en
 *   los tests para que sean instantáneos.
 */
class InMemoryChatRepository(
    private val mode: InMemoryChatMode = InMemoryChatMode.SAMPLE,
    private val latencyMillis: Long = DEFAULT_LATENCY_MILLIS,
) : ChatRepository {

    override suspend fun ask(query: String): ChatLoadResult {
        delay(latencyMillis)
        return when (mode) {
            InMemoryChatMode.SAMPLE -> ChatLoadResult.Success(sampleAnswer(query))
            InMemoryChatMode.UNAVAILABLE -> ChatLoadResult.Failure
        }
    }

    companion object {
        /** Latencia por defecto de la simulación, en milisegundos. */
        const val DEFAULT_LATENCY_MILLIS: Long = 250L
    }
}

/**
 * Clasifica [query] con el corpus de ejemplo y devuelve una respuesta
 * determinista.
 *
 * @param query consulta recibida.
 * @return la respuesta de ejemplo ([ChatStatus.REFUTED]/[ChatStatus.ANSWERED] con
 *   citas, o [ChatStatus.UNFOUNDED] sin citas).
 */
internal fun sampleAnswer(query: String): ChatAnswer = when {
    containsAny(query, REFUTATION_MARKERS) -> ChatAnswer(
        status = ChatStatus.REFUTED,
        text = REFUTED_TEXT,
        citations = sampleCitations(),
    )
    containsAny(query, CORPUS_KEYWORDS) -> ChatAnswer(
        status = ChatStatus.ANSWERED,
        text = ANSWERED_TEXT,
        citations = sampleCitations(),
    )
    else -> ChatAnswer(status = ChatStatus.UNFOUNDED, text = UNFOUNDED_TEXT, citations = emptyList())
}

/**
 * Comprueba si [text] contiene alguno de los [markers], sin distinguir mayúsculas.
 *
 * @param text texto a inspeccionar.
 * @param markers marcadores a buscar.
 * @return `true` si aparece al menos uno.
 */
internal fun containsAny(text: String, markers: List<String>): Boolean =
    markers.any { marker -> text.contains(marker, ignoreCase = true) }

/**
 * Citas de ejemplo del placeholder: dos publicaciones conocidas con su fuente
 * oficial.
 *
 * Es determinista (sin reloj ni aleatoriedad) para que los tests y las `@Preview`
 * puedan afirmar su contenido.
 *
 * @return las citas de ejemplo.
 */
internal fun sampleCitations(): List<ChatCitation> = listOf(
    ChatCitation(
        publicationId = "BOE-A-2026-21002",
        title = "Resolución de 9 de octubre de 2026, de la Secretaría de Estado de Función Pública, " +
            "por la que se convocan pruebas selectivas para el ingreso en el cuerpo general administrativo.",
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21002",
    ),
    ChatCitation(
        publicationId = "BOE-A-2026-21005",
        title = "Extracto de la Orden de 8 de octubre de 2026 por la que se convocan ayudas " +
            "para la renaturalización de espacios urbanos.",
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21005",
    ),
)

/** Marcadores que el placeholder interpreta como afirmación refutada. */
private val REFUTATION_MARKERS = listOf("refut", "falso", "no es cierto")

/** Palabras del corpus de ejemplo que el placeholder interpreta como respuesta. */
private val CORPUS_KEYWORDS = listOf("beca", "empleo", "norma", "plazo")

/** Texto de ejemplo de una respuesta `respondida`. */
private const val ANSWERED_TEXT: String =
    "Según el BOE, la convocatoria de empleo público ofrece 1.250 plazas de acceso libre con un " +
        "plazo de solicitud de 20 días hábiles desde el día siguiente a su publicación."

/** Texto de ejemplo de una respuesta `refutada`. */
private const val REFUTED_TEXT: String =
    "El BOE no respalda esa afirmación: la convocatoria publicada exige titulación de bachillerato " +
        "o equivalente y no admite el acceso sin titulación."

/** Texto de ejemplo de una respuesta `sin_fundamento`. */
private const val UNFOUNDED_TEXT: String =
    "El BOE no contiene información que permita confirmar ni refutar esta consulta."
