package es.aviferdev.datopublico.backend.persistence

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import javax.sql.DataSource

/**
 * Persistencia **mínima** del resumen ciudadano de una publicación (tabla
 * `resumen`).
 *
 * La generación con IA, los guardrails y el contrato definitivo llegan con
 * FT00019/FT00022/FT00023; aquí solo se guarda y se lee el resumen 1:1 de una
 * publicación. `cifras_clave` es un `text[]` nativo.
 */
interface SummaryRepository {
    /**
     * Inserta o actualiza el resumen por su `publicacion_id` (1:1) y devuelve el
     * modelo persistido con su `id`.
     */
    fun save(summary: SummaryEntity): SummaryEntity

    /** Devuelve el resumen de [publicationId], o `null` si no existe. */
    fun findByPublication(publicationId: String): SummaryEntity?
}

/**
 * Fila de la tabla `resumen` (representación de persistencia provisional).
 *
 * [id] es `null` mientras no se ha insertado; [status] sigue la máquina de
 * estados del resumen ciudadano (`pendiente` por defecto) y [confidence] es
 * opcional. [aiNotice] refleja el aviso obligatorio de contenido generado por IA.
 */
data class SummaryEntity(
    val id: Long? = null,
    val publicationId: String,
    val whatChanges: String,
    val whomItAffects: String,
    val keyFigures: List<String> = emptyList(),
    val officialSource: String,
    val aiNotice: Boolean = true,
    val status: String = "pendiente",
    val confidence: Double? = null,
)

/** Lee la fila actual del [ResultSet] como [SummaryEntity]. */
internal fun ResultSet.toSummaryEntity(): SummaryEntity = SummaryEntity(
    id = getLong("id"),
    publicationId = getString("publicacion_id"),
    whatChanges = getString("que_cambia"),
    whomItAffects = getString("a_quien_afecta"),
    keyFigures = readTextArray("cifras_clave"),
    officialSource = getString("fuente_oficial"),
    aiNotice = getBoolean("aviso_ia"),
    status = getString("estado"),
    confidence = getObject("confianza", BigDecimal::class.java)?.toDouble(),
)

/** Lee una columna `text[]` como lista (vacía si es `NULL`). */
private fun ResultSet.readTextArray(column: String): List<String> {
    val array = getArray(column)
    val values = array?.let { data ->
        (data.array as Array<*>).map { element -> element.toString() }
    }.orEmpty()
    array?.free()
    return values
}

/**
 * Implementación JDBC de [SummaryRepository] sobre un [DataSource].
 *
 * Cada método abre y cierra su conexión (autocommit); el pool lo gestiona
 * [es.aviferdev.datopublico.backend.infra.Database].
 */
class SummaryRepositoryJdbc(private val dataSource: DataSource) : SummaryRepository {

    override fun save(summary: SummaryEntity): SummaryEntity {
        val id = dataSource.queryRows(
            UPSERT,
            { statement -> statement.bindParameters(summary) },
            { rows -> rows.getLong("id") },
        ).first()
        return summary.copy(id = id)
    }

    override fun findByPublication(publicationId: String): SummaryEntity? =
        dataSource.queryRows(
            FIND_BY_PUBLICATION,
            { statement -> statement.setString(1, publicationId) },
            { rows -> rows.toSummaryEntity() },
        ).firstOrNull()

    /** Asigna los parámetros de la sentencia de *upsert* de resumen. */
    private fun PreparedStatement.bindParameters(summary: SummaryEntity) {
        setString(1, summary.publicationId)
        setString(2, summary.whatChanges)
        setString(3, summary.whomItAffects)
        setArray(4, connection.createArrayOf("text", summary.keyFigures.toTypedArray()))
        setString(5, summary.officialSource)
        setBoolean(6, summary.aiNotice)
        setString(7, summary.status)
        setObject(8, summary.confidence, Types.NUMERIC)
    }

    private companion object {
        const val UPSERT = """
            INSERT INTO resumen (
                publicacion_id, que_cambia, a_quien_afecta, cifras_clave,
                fuente_oficial, aviso_ia, estado, confianza
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (publicacion_id) DO UPDATE SET
                que_cambia = EXCLUDED.que_cambia,
                a_quien_afecta = EXCLUDED.a_quien_afecta,
                cifras_clave = EXCLUDED.cifras_clave,
                fuente_oficial = EXCLUDED.fuente_oficial,
                aviso_ia = EXCLUDED.aviso_ia,
                estado = EXCLUDED.estado,
                confianza = EXCLUDED.confianza,
                actualizado_en = now()
            RETURNING id
        """

        const val FIND_BY_PUBLICATION = """
            SELECT id, publicacion_id, que_cambia, a_quien_afecta, cifras_clave,
                   fuente_oficial, aviso_ia, estado, confianza
            FROM resumen
            WHERE publicacion_id = ?
        """
    }
}
