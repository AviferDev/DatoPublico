package es.aviferdev.datopublico.backend.ingesta.sumario

import es.aviferdev.datopublico.model.SeccionBoeDto
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
    suspend fun obtenerSumario(fecha: LocalDate): List<EntradaSumario>
}

/** Error al obtener el sumario del BOE (red, estado inesperado o cuerpo ilegible). */
class BoeSumarioException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Traduce el sobre de wire del BOE al modelo interno, aplanando la jerarquía
 * sección → departamento → (epígrafe →) entrada. No se descarta ninguna sección.
 */
internal fun BoeSumarioResponseDto.toEntradasSumario(fecha: LocalDate): List<EntradaSumario> {
    val fechaIso = fecha.toString()
    return data?.sumario?.diario.orEmpty().flatMap { diario ->
        diario.seccion.flatMap { seccion -> seccion.entradas(fechaIso) }
    }
}

private fun BoeSeccionDto.entradas(fechaIso: String): List<EntradaSumario> =
    seccionBoeDe(codigo)?.let { seccionInterna ->
        departamento.flatMap { it.entradas(fechaIso, seccionInterna) }
    }.orEmpty()

private fun BoeDepartamentoDto.entradas(
    fechaIso: String,
    seccion: SeccionBoeDto,
): List<EntradaSumario> = buildList {
    item.forEach { add(it.toEntrada(fechaIso, seccion, organismo = nombre, epigrafe = null)) }
    epigrafe.forEach { epigrafeDto ->
        epigrafeDto.item.forEach {
            add(it.toEntrada(fechaIso, seccion, organismo = nombre, epigrafe = epigrafeDto.nombre))
        }
    }
    texto?.item.orEmpty().forEach {
        add(it.toEntrada(fechaIso, seccion, organismo = nombre, epigrafe = null))
    }
}

private fun BoeSumarioItemDto.toEntrada(
    fechaIso: String,
    seccion: SeccionBoeDto,
    organismo: String?,
    epigrafe: String?,
): EntradaSumario = EntradaSumario(
    identificador = identificador.orEmpty(),
    control = control,
    titulo = titulo.orEmpty(),
    fechaPublicacion = fechaIso,
    seccion = seccion,
    organismo = organismo,
    epigrafe = epigrafe,
    urlOficial = urlHtml ?: urlPdf?.texto.orEmpty(),
    urlXml = urlXml,
    urlPdf = urlPdf?.texto,
)

/** Mapea el código de sección del BOE a la sección del contrato; `null` si es ajeno. */
private fun seccionBoeDe(codigo: String?): SeccionBoeDto? = when (codigo) {
    "1" -> SeccionBoeDto.I
    "2A" -> SeccionBoeDto.II_A
    "2B" -> SeccionBoeDto.II_B
    "3" -> SeccionBoeDto.III
    "4" -> SeccionBoeDto.IV
    "5A" -> SeccionBoeDto.V_A
    "5B" -> SeccionBoeDto.V_B
    "5C" -> SeccionBoeDto.V_C
    else -> null // un código ajeno al sumario del BOE no es corpus.
}
