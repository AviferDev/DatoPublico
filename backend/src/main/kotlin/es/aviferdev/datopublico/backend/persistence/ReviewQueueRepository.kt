package es.aviferdev.datopublico.backend.persistence

import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * Persistencia **mínima** de la cola de revisión (tabla `cola_revision`).
 *
 * La lógica de guardrails, confianza y colocación en la cola llega con
 * FT00022/FT00023; aquí solo se guardan entradas ligadas a un resumen y se leen
 * por su resumen. El borrado de un resumen (o de su publicación) arrastra sus
 * entradas por `ON DELETE CASCADE`.
 */
interface ReviewQueueRepository {
    /**
     * Inserta una entrada de revisión ligada a un resumen y la devuelve con su
     * `id`.
     */
    fun save(entry: ReviewQueueEntity): ReviewQueueEntity

    /** Lista las entradas de revisión de [summaryId]. */
    fun listBySummary(summaryId: Long): List<ReviewQueueEntity>
}

/**
 * Fila de la tabla `cola_revision` (representación de persistencia provisional).
 *
 * [id] es `null` mientras no se ha insertado; [status] usa `pendiente` por
 * defecto y [reason] es opcional.
 */
data class ReviewQueueEntity(
    val id: Long? = null,
    val summaryId: Long,
    val reason: String?,
    val status: String = "pendiente",
)

/** Lee la fila actual del [ResultSet] como [ReviewQueueEntity]. */
internal fun ResultSet.toReviewQueueEntity(): ReviewQueueEntity = ReviewQueueEntity(
    id = getLong("id"),
    summaryId = getLong("resumen_id"),
    reason = getString("motivo"),
    status = getString("estado"),
)

/**
 * Implementación JDBC de [ReviewQueueRepository] sobre un [DataSource].
 *
 * Cada método abre y cierra su conexión (autocommit); el pool lo gestiona
 * [es.aviferdev.datopublico.backend.infra.Database].
 */
class ReviewQueueRepositoryJdbc(private val dataSource: DataSource) : ReviewQueueRepository {

    override fun save(entry: ReviewQueueEntity): ReviewQueueEntity {
        val id = dataSource.queryRows(
            INSERT,
            { statement -> statement.bindParameters(entry) },
            { rows -> rows.getLong("id") },
        ).first()
        return entry.copy(id = id)
    }

    override fun listBySummary(summaryId: Long): List<ReviewQueueEntity> =
        dataSource.queryRows(
            LIST_BY_SUMMARY,
            { statement -> statement.setLong(1, summaryId) },
            { rows -> rows.toReviewQueueEntity() },
        )

    /** Asigna los parámetros de la sentencia de inserción de la cola. */
    private fun PreparedStatement.bindParameters(entry: ReviewQueueEntity) {
        setLong(1, entry.summaryId)
        setString(2, entry.reason)
        setString(3, entry.status)
    }

    private companion object {
        const val INSERT = """
            INSERT INTO cola_revision (resumen_id, motivo, estado)
            VALUES (?, ?, ?)
            RETURNING id
        """

        const val LIST_BY_SUMMARY = """
            SELECT id, resumen_id, motivo, estado
            FROM cola_revision
            WHERE resumen_id = ?
            ORDER BY id
        """
    }
}
