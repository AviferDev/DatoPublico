package es.aviferdev.datopublico.feature.detail.data

import es.aviferdev.datopublico.feature.detail.domain.CitizenSummary
import es.aviferdev.datopublico.feature.detail.domain.DetailLoadResult
import es.aviferdev.datopublico.feature.detail.domain.PublicationDetail
import es.aviferdev.datopublico.feature.feed.domain.Deadline
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory
import kotlinx.coroutines.delay

/**
 * Comportamiento del repositorio en memoria; permite ejercitar los estados de la
 * pantalla de detalle sin red ni base de datos.
 */
enum class InMemoryDetailMode {
    /** Devuelve el detalle de ejemplo de las publicaciones conocidas. */
    SAMPLE,

    /** Devuelve siempre «no encontrada». */
    NOT_FOUND,

    /** Devuelve un fallo tipado (estado de error con reintento). */
    UNAVAILABLE,
}

/**
 * Implementación **en memoria** de [DetailRepository] con datos de ejemplo.
 *
 * Es un **placeholder** hasta FT00035, que implementará el cliente HTTP real y la
 * caché offline sobre el contrato de `:shared`. Sirve para montar y verificar la
 * UI del detalle sin backend (el detalle real todavía sale vacío porque el
 * pipeline de resumen no está cableado).
 *
 * En modo [InMemoryDetailMode.SAMPLE], un identificador desconocido devuelve
 * [DetailLoadResult.NotFound], espejando el `404` del endpoint FT00027.
 *
 * @param mode modo de comportamiento (contenido, no encontrada o fallo).
 * @param latencyMillis latencia simulada antes de responder; se inyecta **0** en
 *   los tests para que sean instantáneos.
 */
class InMemoryDetailRepository(
    private val mode: InMemoryDetailMode = InMemoryDetailMode.SAMPLE,
    private val latencyMillis: Long = DEFAULT_LATENCY_MILLIS,
) : DetailRepository {
    override suspend fun load(id: String): DetailLoadResult {
        delay(latencyMillis)
        return when (mode) {
            InMemoryDetailMode.SAMPLE -> sampleResult(id)
            InMemoryDetailMode.NOT_FOUND -> DetailLoadResult.NotFound
            InMemoryDetailMode.UNAVAILABLE -> DetailLoadResult.Failure
        }
    }

    /**
     * Busca el detalle de ejemplo por identificador.
     *
     * @param id identificador solicitado.
     * @return el detalle de ejemplo, o [DetailLoadResult.NotFound] si no existe.
     */
    private fun sampleResult(id: String): DetailLoadResult {
        val detail = sampleDetails().firstOrNull { candidate -> candidate.id == id }
        return detail?.let { found -> DetailLoadResult.Success(found) } ?: DetailLoadResult.NotFound
    }

    companion object {
        /** Latencia por defecto de la simulación, en milisegundos. */
        const val DEFAULT_LATENCY_MILLIS: Long = 250L
    }
}

/**
 * Detalles de ejemplo del cliente: una convocatoria con **plazo** y una norma
 * **sin** plazo, ambas con resumen estructurado y fuente oficial.
 *
 * Es determinista (sin reloj ni aleatoriedad) para que los tests y las `@Preview`
 * puedan afirmar su contenido.
 *
 * @return los detalles de ejemplo.
 */
internal fun sampleDetails(): List<PublicationDetail> =
    listOf(publicEmploymentDetail(), normativeRegulationDetail())

/** Convocatoria de empleo público con plazo y resumen ciudadano completo. */
private fun publicEmploymentDetail(): PublicationDetail = PublicationDetail(
    id = "BOE-A-2026-21002",
    title = "Resolución de 9 de octubre de 2026, de la Secretaría de Estado de Función Pública, " +
        "por la que se convocan pruebas selectivas para el ingreso en el cuerpo general administrativo.",
    category = PublicationCategory.PUBLIC_EMPLOYMENT_AND_EXAMS,
    publishedDate = "2026-10-13",
    summary = CitizenSummary(
        whatChanges = "Se convocan pruebas selectivas de acceso libre para cubrir 1.250 plazas " +
            "del cuerpo general administrativo, con fase de oposición y concurso de méritos.",
        whoIsAffected = "Personas con titulación de bachillerato o equivalente que quieran " +
            "acceder a un puesto de funcionario en la Administración General del Estado.",
        keyFigures = listOf(
            "1.250 plazas de acceso libre.",
            "Plazo de solicitud de 20 días hábiles.",
            "Tasa de examen: 15,60 euros (bonificada al 50 % para familias numerosas).",
        ),
    ),
    deadline = Deadline(limitDate = "2026-10-30"),
    officialSourceUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21002",
    showsAiNotice = true,
)

/** Norma sin plazo, con resumen estructurado y fuente oficial. */
private fun normativeRegulationDetail(): PublicationDetail = PublicationDetail(
    id = "BOE-A-2026-21001",
    title = "Real Decreto 900/2026, de 12 de octubre, por el que se regula el acceso " +
        "a los servicios digitales de las administraciones públicas.",
    category = PublicationCategory.NORMATIVE_REGULATION,
    publishedDate = "2026-10-13",
    summary = CitizenSummary(
        whatChanges = "Regula el derecho de la ciudadanía a relacionarse digitalmente con la " +
            "Administración y fija los plazos de adaptación de las sedes electrónicas.",
        whoIsAffected = "Todas las administraciones públicas y las personas que realicen " +
            "trámites por vía electrónica.",
        keyFigures = listOf(
            "Adaptación de las sedes electrónicas en 12 meses.",
            "Entrada en vigor el día siguiente al de su publicación.",
        ),
    ),
    deadline = null,
    officialSourceUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21001",
    showsAiNotice = true,
)
