package es.aviferdev.datopublico.backend.ingestion.summary

import es.aviferdev.datopublico.model.BoeSectionDto
import java.time.LocalDate

/**
 * Cliente del sumario diario del BOE (API de datos abiertos, solo lectura).
 *
 * Devuelve las entradas de **todo el sumario** (secciones I, II.A, II.B, III, IV,
 * V.A, V.B y V.C) para una fecha. No persiste, no clasifica ni descarga el texto
 * completo.
 */
interface BoeSummaryClient {
    /**
     * Obtiene las entradas del sumario del BOE publicadas en [fecha].
     *
     * @return lista de entradas de todas las secciones del BOE; **vacía** si ese
     *   día no hay publicación (`HTTP 404`).
     * @throws BoeSummaryException si la fuente falla (red) o responde con un
     *   estado distinto de `200`/`404`, o con un cuerpo ilegible.
     */
    suspend fun fetchSummary(date: LocalDate): List<SummaryEntry>
}

/** Error al obtener el sumario del BOE (red, estado inesperado o cuerpo ilegible). */
class BoeSummaryException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Traduce el sobre de wire del BOE al modelo interno, aplanando la jerarquía
 * sección → departamento → (epígrafe →) entrada. No se descarta ninguna sección.
 */
internal fun BoeSummaryResponseDto.toSummaryEntries(date: LocalDate): List<SummaryEntry> {
    val isoDate = date.toString()
    return data?.summary?.daily.orEmpty().flatMap { daily ->
        daily.section.flatMap { section -> section.entries(isoDate) }
    }
}

private fun BoeSectionNodeDto.entries(isoDate: String): List<SummaryEntry> =
    boeSectionOf(code)?.let { seccionInterna ->
        department.flatMap { it.entries(isoDate, seccionInterna) }
    }.orEmpty()

private fun BoeDepartmentDto.entries(
    isoDate: String,
    section: BoeSectionDto,
): List<SummaryEntry> = buildList {
    item.forEach { add(it.toEntry(isoDate, section, issuingBody = name, heading = null)) }
    heading.forEach { epigrafeDto ->
        epigrafeDto.item.forEach {
            add(it.toEntry(isoDate, section, issuingBody = name, heading = epigrafeDto.name))
        }
    }
    text?.item.orEmpty().forEach {
        add(it.toEntry(isoDate, section, issuingBody = name, heading = null))
    }
}

private fun BoeSummaryItemDto.toEntry(
    isoDate: String,
    section: BoeSectionDto,
    issuingBody: String?,
    heading: String?,
): SummaryEntry = SummaryEntry(
    identifier = identifier.orEmpty(),
    control = control,
    title = title.orEmpty(),
    publicationDate = isoDate,
    section = section,
    issuingBody = issuingBody,
    heading = heading,
    officialUrl = htmlUrl ?: pdfUrl?.text.orEmpty(),
    xmlUrl = xmlUrl,
    pdfUrl = pdfUrl?.text,
)

/** Mapea el código de sección del BOE a la sección del contrato; `null` si es ajeno. */
private fun boeSectionOf(code: String?): BoeSectionDto? = when (code) {
    "1" -> BoeSectionDto.I
    "2A" -> BoeSectionDto.II_A
    "2B" -> BoeSectionDto.II_B
    "3" -> BoeSectionDto.III
    "4" -> BoeSectionDto.IV
    "5A" -> BoeSectionDto.V_A
    "5B" -> BoeSectionDto.V_B
    "5C" -> BoeSectionDto.V_C
    else -> null // un código ajeno al sumario del BOE no es corpus.
}
