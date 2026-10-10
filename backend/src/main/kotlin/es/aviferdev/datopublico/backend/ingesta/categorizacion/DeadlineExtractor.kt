package es.aviferdev.datopublico.backend.ingesta.categorizacion

import es.aviferdev.datopublico.model.PlazoDto
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Extractor **heurístico y acotado** del plazo de solicitud de una convocatoria
 * del BOE (empleo público, becas o subvenciones).
 *
 * Es **puro y determinista**: no usa IA, ni red, ni base de datos, ni reloj del
 * sistema; recibe el texto y la fecha de publicación y devuelve un [PlazoDto] o
 * `null`. **Nunca inventa una fecha**: si el patrón no es fiable, devuelve `null`.
 *
 * Reconoce dos formas de plazo:
 * - **Días relativos**: `plazo de <N> días [hábiles|naturales]` con la coletilla
 *   `a contar|contados|a partir … el día siguiente al de la publicación`. `N` es
 *   dígito o palabra castellana (1–30). `naturales` suma días naturales;
 *   `hábiles` cuenta el N-ésimo día laborable **estrictamente posterior** a la
 *   fecha de publicación (se saltan sábados y domingos; **no** se modelan
 *   festivos).
 * - **Fecha explícita**: `hasta el <d> de <mes> de <aaaa>`, `hasta el <dd/mm/aaaa>`
 *   o `hasta el <dd-mm-aaaa>`.
 *
 * **Puerta de contexto (anti-falsos-positivos)**: solo se extrae si el fragmento
 * habla de «presentación de solicitudes» / «presentar … solicitud/documentación»,
 * y se descarta si casa «plazo de ejecución», «de resolución», «para resolver»,
 * «recurso», «subsanación», «alegaciones» o «informe».
 *
 * @see PublicationClassifier clasificador complementario por categoría.
 */
class DeadlineExtractor {

    /**
     * Extrae el plazo de solicitud de [text], referido a [publishedDate].
     *
     * @param text texto completo de la publicación (normalizado por párrafos), o
     *   `null`/vacío si la fuente no trae texto.
     * @param publishedDate fecha de publicación ISO-8601 (`2024-01-02`).
     * @return plazo de solicitud, o `null` si no hay un plazo fiable.
     */
    fun extract(text: String?, publishedDate: String): PlazoDto? {
        val paragraphs = text?.split(PARAGRAPH_SEPARATOR).orEmpty()
        return paragraphs.asSequence().mapNotNull { relativeDeadline(it, publishedDate) }.firstOrNull()
            ?: paragraphs.asSequence().mapNotNull { explicitDeadline(it) }.firstOrNull()
    }

    /**
     * Plazo por **días relativos** de un párrafo, o `null` si no pasa las puertas.
     *
     * @param paragraph párrafo original (para construir la descripción legible).
     * @param publishedDate fecha de publicación ISO-8601.
     */
    private fun relativeDeadline(paragraph: String, publishedDate: String): PlazoDto? {
        val normalized = normalize(paragraph)
        return RELATIVE_DAYS.find(normalized)?.let { match ->
            val days = daysOf(match.groupValues[1], match.groupValues[2])
            val confirmed = days?.takeIf { passesDayGates(normalized, match) }
            confirmed?.let { count ->
                PlazoDto(
                    fechaLimite = deadlineDate(publishedDate, count, isBusinessDays(match.groupValues[3])),
                    descripcion = description(paragraph),
                )
            }
        }
    }

    /** Puerta de contexto + coletilla de publicación que confirma un plazo por días. */
    private fun passesDayGates(normalized: String, match: MatchResult): Boolean {
        val tail = normalized.substring(match.range.last + 1)
        return hasRequestContext(normalized) &&
            EXCLUDED.none { normalized.contains(it) } &&
            tail.contains(PUBLICATION_SUFFIX) &&
            COUNTING_SUFFIX.containsMatchIn(tail)
    }

    /** ¿El texto habla de presentar solicitud/documentación? (puerta de contexto). */
    private fun hasRequestContext(normalized: String): Boolean =
        normalized.contains("presentacion de solicitudes") ||
            (PRESENTAR.containsMatchIn(normalized) && REQUEST_OBJECT.containsMatchIn(normalized))

    /** Plazo por **fecha explícita** de un párrafo, o `null` si no pasa las puertas. */
    private fun explicitDeadline(paragraph: String): PlazoDto? {
        val normalized = normalize(paragraph)
        val date = explicitDate(normalized)
            ?.takeIf { hasRequestContext(normalized) && EXCLUDED.none { phrase -> normalized.contains(phrase) } }
        return date?.let { PlazoDto(fechaLimite = it, descripcion = description(paragraph)) }
    }

    /** Fecha ISO de la primera fecha explícita reconocida en [normalized], o `null`. */
    private fun explicitDate(normalized: String): String? =
        NUMERIC_DATE.find(normalized)?.let { match ->
            isoDate(match.groupValues[1], match.groupValues[2], match.groupValues[3])
        } ?: WORD_DATE.find(normalized)?.let { match ->
            MONTHS[match.groupValues[2]]?.let { month ->
                isoDate(match.groupValues[1], month.toString(), match.groupValues[3])
            }
        }

    /** Convierte día/mes/año en ISO-8601; `null` si la fecha no es válida. */
    private fun isoDate(day: String, month: String, year: String): String? =
        runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()).toString() }.getOrNull()

    /** Días (1–30) desde el primer token numérico o la palabra castellana, o `null`. */
    private fun daysOf(first: String, second: String): Int? =
        first.toIntOrNull()?.takeIf { it in MIN_DAYS..MAX_DAYS }
            ?: NUMBER_WORDS[first]
            ?: second.toIntOrNull()?.takeIf { it in MIN_DAYS..MAX_DAYS }
            ?: NUMBER_WORDS[second]

    /** Fecha límite ISO-8601 sumando días naturales o laborables a [publishedDate]. */
    private fun deadlineDate(publishedDate: String, days: Int, businessDays: Boolean): String {
        val start = LocalDate.parse(publishedDate)
        val limit = if (businessDays) businessDateAfter(start, days) else start.plusDays(days.toLong())
        return limit.toString()
    }

    /** N-ésimo día laborable (lun–vie) **estrictamente posterior** a [start]. */
    private fun businessDateAfter(start: LocalDate, days: Int): LocalDate =
        generateSequence(start) { it.plusDays(1) }
            .drop(1)
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .drop(days - 1)
            .first()

    /** `true` si la unidad es «hábiles» (texto ya normalizado sin acentos). */
    private fun isBusinessDays(unit: String): Boolean = unit.startsWith("hab")

    /** Descripción legible y acotada: el párrafo en una línea, truncado. */
    private fun description(paragraph: String): String {
        val clean = paragraph.trim().replace(WHITESPACE, " ")
        return if (clean.length <= MAX_DESCRIPTION) clean else clean.take(MAX_DESCRIPTION).trimEnd() + "…"
    }

    /** Minúsculas, sin acentos y con espacios colapsados (idéntico al clasificador). */
    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase()
            .replace(WHITESPACE, " ")
            .trim()

    private companion object {
        /** Separación de párrafos con la que el parser normaliza el texto del BOE. */
        val PARAGRAPH_SEPARATOR = Regex("\\n+")

        /** Marcas diacríticas que [Normalizer] separa tras descomponer en NFD. */
        val COMBINING_MARKS = Regex("\\p{Mn}+")

        /** Espacios repetidos. */
        val WHITESPACE = Regex("\\s+")

        /** `plazo … de <N>[ (N)] días [hábiles|naturales]` (texto normalizado). */
        val RELATIVE_DAYS = Regex(
            "plazo\\s+(?:\\S+\\s+){0,8}?de\\s+(\\d{1,2}|[a-z]+)\\s*" +
                "(?:\\(\\s*(\\d{1,2}|[a-z]+)\\s*\\))?\\s*dias(?:\\s+(habiles|naturales))?",
        )

        /** Coletillas que ligan el cómputo al día siguiente a la publicación. */
        val COUNTING_SUFFIX = Regex("\\b(?:a contar|contados?|contando|a partir)\\b")

        /** Fecha explícita `hasta el dd/mm/aaaa` o `hasta el dd-mm-aaaa`. */
        val NUMERIC_DATE = Regex("hasta el (\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})")

        /** Fecha explícita `hasta el d de <mes> de aaaa` (meses en castellano). */
        val WORD_DATE = Regex(
            "hasta el (\\d{1,2}) de (enero|febrero|marzo|abril|mayo|junio|julio|agosto|" +
                "septiembre|setiembre|octubre|noviembre|diciembre) de (\\d{4})",
        )

        /** Raíz «present…» (cubre presentar, presentará, presentación…). */
        val PRESENTAR = Regex("present\\w*")

        /** Objeto de la presentación que activa la puerta de contexto. */
        val REQUEST_OBJECT = Regex("solicitud|documentacion")

        /** Coletilla literal de publicación que confirma un plazo por días. */
        const val PUBLICATION_SUFFIX = "dia siguiente al de la publicacion"

        /** Longitud máxima de la descripción del plazo. */
        const val MAX_DESCRIPTION = 300

        /** Límite de días soportado por la palabra castellana (mapa 1–30). */
        const val MIN_DAYS = 1
        const val MAX_DAYS = 30

        /** Expresiones cuyo contexto descarta el plazo (falsos positivos). */
        val EXCLUDED = listOf(
            "plazo de ejecucion",
            "de resolucion",
            "para resolver",
            "recurso",
            "subsanacion",
            "alegaciones",
            "informe",
        )

        /** Mes castellano → número de mes. */
        val MONTHS = mapOf(
            "enero" to 1,
            "febrero" to 2,
            "marzo" to 3,
            "abril" to 4,
            "mayo" to 5,
            "junio" to 6,
            "julio" to 7,
            "agosto" to 8,
            "septiembre" to 9,
            "setiembre" to 9,
            "octubre" to 10,
            "noviembre" to 11,
            "diciembre" to 12,
        )

        /** Palabra castellana de 1–30 días → número (texto normalizado sin acentos). */
        val NUMBER_WORDS = mapOf(
            "uno" to 1,
            "un" to 1,
            "dos" to 2,
            "tres" to 3,
            "cuatro" to 4,
            "cinco" to 5,
            "seis" to 6,
            "siete" to 7,
            "ocho" to 8,
            "nueve" to 9,
            "diez" to 10,
            "once" to 11,
            "doce" to 12,
            "trece" to 13,
            "catorce" to 14,
            "quince" to 15,
            "dieciseis" to 16,
            "diecisiete" to 17,
            "dieciocho" to 18,
            "diecinueve" to 19,
            "veinte" to 20,
            "veintiuno" to 21,
            "veintiun" to 21,
            "veintidos" to 22,
            "veintitres" to 23,
            "veinticuatro" to 24,
            "veinticinco" to 25,
            "veintiseis" to 26,
            "veintisiete" to 27,
            "veintiocho" to 28,
            "veintinueve" to 29,
            "treinta" to 30,
        )
    }
}
