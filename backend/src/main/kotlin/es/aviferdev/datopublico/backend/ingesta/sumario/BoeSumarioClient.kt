package es.aviferdev.datopublico.backend.ingesta.sumario

import es.aviferdev.datopublico.model.SeccionBoeDto
import java.time.LocalDate

/**
 * Cliente del sumario diario del BOE (API de datos abiertos, solo lectura).
 *
 * Devuelve las entradas de las secciones del corpus (I, II.A, II.B, III y V.B)
 * para una fecha. No persiste, no clasifica ni descarga el texto completo.
 */
interface BoeSumarioClient {
    /**
     * Obtiene las entradas del sumario del BOE publicadas en [fecha].
     *
     * @return lista de entradas de las secciones I/II.A/II.B/III/V.B; **vacía** si
     *   ese día no hay publicación (`HTTP 404`).
     * @throws BoeSumarioException si la fuente falla (red) o responde con un
     *   estado distinto de `200`/`404`, o con un cuerpo ilegible.
     */
    suspend fun obtenerSumario(fecha: LocalDate): List<EntradaSumario>
}

/** Error al obtener el sumario del BOE (red, estado inesperado o cuerpo ilegible). */
class BoeSumarioException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Traduce el sobre de wire del BOE al modelo interno, descartando las secciones
 * excluidas (IV y V.A) y aplanando la jerarquía
 * sección → departamento → epígrafe → entrada.
 */
internal fun BoeSumarioResponseDto.toEntradasSumario(fecha: LocalDate): List<EntradaSumario> {
    val fechaIso = fecha.toString()
    return data?.sumario?.diario.orEmpty().flatMap { diario ->
        diario.seccion.flatMap { seccion -> seccion.entradas(fechaIso) }
    }
}

private fun BoeSeccionDto.entradas(fechaIso: String): List<EntradaSumario> {
    val seccionInterna = seccionBoeDe(codigo) ?: return emptyList()
    return departamento.flatMap { it.entradas(fechaIso, seccionInterna) }
}

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
    urlOficial = urlHtml.orEmpty(),
    urlXml = urlXml,
)

/** Mapea el código de sección del BOE a la sección del corpus; `null` si se excluye. */
private fun seccionBoeDe(codigo: String?): SeccionBoeDto? = when (codigo) {
    "1" -> SeccionBoeDto.I
    "2A" -> SeccionBoeDto.II_A
    "2B" -> SeccionBoeDto.II_B
    "3" -> SeccionBoeDto.III
    "5B" -> SeccionBoeDto.V_B
    else -> null // "4" (IV) y "5A" (V.A) quedan excluidas; el resto no es corpus.
}
