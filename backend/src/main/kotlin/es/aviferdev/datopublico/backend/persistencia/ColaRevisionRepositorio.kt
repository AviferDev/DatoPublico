package es.aviferdev.datopublico.backend.persistencia

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
interface ColaRevisionRepositorio {
    /**
     * Inserta una entrada de revisión ligada a un resumen y la devuelve con su
     * `id`.
     */
    fun guardar(entrada: ColaRevisionEntity): ColaRevisionEntity

    /** Lista las entradas de revisión de [resumenId]. */
    fun listarPorResumen(resumenId: Long): List<ColaRevisionEntity>
}

/**
 * Fila de la tabla `cola_revision` (representación de persistencia provisional).
 *
 * [id] es `null` mientras no se ha insertado; [estado] usa `pendiente` por
 * defecto y [motivo] es opcional.
 */
data class ColaRevisionEntity(
    val id: Long? = null,
    val resumenId: Long,
    val motivo: String?,
    val estado: String = "pendiente",
)

/** Lee la fila actual del [ResultSet] como [ColaRevisionEntity]. */
internal fun ResultSet.aColaRevisionEntity(): ColaRevisionEntity = ColaRevisionEntity(
    id = getLong("id"),
    resumenId = getLong("resumen_id"),
    motivo = getString("motivo"),
    estado = getString("estado"),
)

/**
 * Implementación JDBC de [ColaRevisionRepositorio] sobre un [DataSource].
 *
 * Cada método abre y cierra su conexión (autocommit); el pool lo gestiona
 * [es.aviferdev.datopublico.backend.infra.BaseDatos].
 */
class ColaRevisionRepositorioJdbc(private val dataSource: DataSource) : ColaRevisionRepositorio {

    override fun guardar(entrada: ColaRevisionEntity): ColaRevisionEntity {
        val id = dataSource.consultarFilas(
            INSERTAR,
            { sentencia -> sentencia.fijarParametros(entrada) },
            { filas -> filas.getLong("id") },
        ).first()
        return entrada.copy(id = id)
    }

    override fun listarPorResumen(resumenId: Long): List<ColaRevisionEntity> =
        dataSource.consultarFilas(
            LISTAR_POR_RESUMEN,
            { sentencia -> sentencia.setLong(1, resumenId) },
            { filas -> filas.aColaRevisionEntity() },
        )

    /** Asigna los parámetros de la sentencia de inserción de la cola. */
    private fun PreparedStatement.fijarParametros(entrada: ColaRevisionEntity) {
        setLong(1, entrada.resumenId)
        setString(2, entrada.motivo)
        setString(3, entrada.estado)
    }

    private companion object {
        const val INSERTAR = """
            INSERT INTO cola_revision (resumen_id, motivo, estado)
            VALUES (?, ?, ?)
            RETURNING id
        """

        const val LISTAR_POR_RESUMEN = """
            SELECT id, resumen_id, motivo, estado
            FROM cola_revision
            WHERE resumen_id = ?
            ORDER BY id
        """
    }
}
