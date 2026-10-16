package es.aviferdev.datopublico.backend.ingesta.sumario

import es.aviferdev.datopublico.model.BoeSectionDto
import java.time.LocalDate

/**
 * Cliente del sumario diario del BOE (API de datos abiertos, solo lectura).
 *
 * Devuelve las entradas de **todo el sumario** (secciones I, II.A, II.B, III, IV,
 * V.A, V.B y V.C) para una fecha. No persiste, no clasifica ni descarga el texto
 * completo.
 */
interface BoeSumarioClient {
    /**
     * Obtiene las entradas del sumario del BOE publicadas en [fecha].
     *
     * @return lista de entradas de todas las secciones del BOE; **vacía** si ese
     *   día no hay publicación (`HTTP 404`).
     * @throws BoeSumarioException si la fuente falla (red) o responde con un
     *   estado distinto de `200`/`404`, o con un cuerpo ilegible.
     */
    suspend fun obtenerSumario(date: LocalDate): List<EntradaSumario>
}

/** Error al obtener el sumario del BOE (red, estado inesperado o cuerpo ilegible). */
class BoeSumarioException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Traduce el sobre de wire del BOE al modelo interno, aplanando la jerarquía
 * sección → departamento → (epígrafe →) entrada. No se descarta ninguna sección.
 */
internal fun BoeSumarioResponseDto.toEntradasSumario(date: LocalDate): List<EntradaSumario> {
    val fechaIso = date.toString()
    return data?.sumario?.diario.orEmpty().flatMap { diario ->
        diario.section.flatMap { section -> section.entradas(fechaIso) }
    }
}

private fun BoeSeccionDto.entradas(fechaIso: String): List<EntradaSumario> =
    seccionBoeDe(codigo)?.let { seccionInterna ->
        departamento.flatMap { it.entradas(fechaIso, seccionInterna) }
    }.orEmpty()

private fun BoeDepartamentoDto.entradas(
    fechaIso: String,
    section: BoeSectionDto,
): List<EntradaSumario> = buildList {
    item.forEach { add(it.toEntrada(fechaIso, section, issuingBody = nombre, heading = null)) }
    heading.forEach { epigrafeDto ->
        epigrafeDto.item.forEach {
            add(it.toEntrada(fechaIso, section, issuingBody = nombre, heading = epigrafeDto.nombre))
        }
    }
    texto?.item.orEmpty().forEach {
        add(it.toEntrada(fechaIso, section, issuingBody = nombre, heading = null))
    }
}

private fun BoeSumarioItemDto.toEntrada(
    fechaIso: String,
    section: BoeSectionDto,
    issuingBody: String?,
    heading: String?,
): EntradaSumario = EntradaSumario(
    identificador = identificador.orEmpty(),
    control = control,
    title = title.orEmpty(),
    publicationDate = fechaIso,
    section = section,
    issuingBody = issuingBody,
    heading = heading,
    officialUrl = urlHtml ?: urlPdf?.texto.orEmpty(),
    urlXml = urlXml,
    urlPdf = urlPdf?.texto,
)

/** Mapea el código de sección del BOE a la sección del contrato; `null` si es ajeno. */
private fun seccionBoeDe(codigo: String?): BoeSectionDto? = when (codigo) {
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
