package es.aviferdev.datopublico.backend.persistencia

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
interface ResumenRepositorio {
    /**
     * Inserta o actualiza el resumen por su `publicacion_id` (1:1) y devuelve el
     * modelo persistido con su `id`.
     */
    fun guardar(resumen: ResumenEntity): ResumenEntity

    /** Devuelve el resumen de [publicacionId], o `null` si no existe. */
    fun buscarPorPublicacion(publicacionId: String): ResumenEntity?
}

/**
 * Fila de la tabla `resumen` (representación de persistencia provisional).
 *
 * [id] es `null` mientras no se ha insertado; [estado] sigue la máquina de
 * estados del resumen ciudadano (`pendiente` por defecto) y [confianza] es
 * opcional. [avisoIa] refleja el aviso obligatorio de contenido generado por IA.
 */
data class ResumenEntity(
    val id: Long? = null,
    val publicacionId: String,
    val queCambia: String,
    val aQuienAfecta: String,
    val cifrasClave: List<String> = emptyList(),
    val fuenteOficial: String,
    val avisoIa: Boolean = true,
    val estado: String = "pendiente",
    val confianza: Double? = null,
)

/** Lee la fila actual del [ResultSet] como [ResumenEntity]. */
internal fun ResultSet.aResumenEntity(): ResumenEntity = ResumenEntity(
    id = getLong("id"),
    publicacionId = getString("publicacion_id"),
    queCambia = getString("que_cambia"),
    aQuienAfecta = getString("a_quien_afecta"),
    cifrasClave = leerTextoArray("cifras_clave"),
    fuenteOficial = getString("fuente_oficial"),
    avisoIa = getBoolean("aviso_ia"),
    estado = getString("estado"),
    confianza = getObject("confianza", BigDecimal::class.java)?.toDouble(),
)

/** Lee una columna `text[]` como lista (vacía si es `NULL`). */
private fun ResultSet.leerTextoArray(columna: String): List<String> {
    val array = getArray(columna)
    val valores = array?.let { datos ->
        (datos.array as Array<*>).map { elemento -> elemento.toString() }
    }.orEmpty()
    array?.free()
    return valores
}

/**
 * Implementación JDBC de [ResumenRepositorio] sobre un [DataSource].
 *
 * Cada método abre y cierra su conexión (autocommit); el pool lo gestiona
 * [es.aviferdev.datopublico.backend.infra.BaseDatos].
 */
class ResumenRepositorioJdbc(private val dataSource: DataSource) : ResumenRepositorio {

    override fun guardar(resumen: ResumenEntity): ResumenEntity {
        val id = dataSource.consultarFilas(
            INSERTAR_O_ACTUALIZAR,
            { sentencia -> sentencia.fijarParametros(resumen) },
            { filas -> filas.getLong("id") },
        ).first()
        return resumen.copy(id = id)
    }

    override fun buscarPorPublicacion(publicacionId: String): ResumenEntity? =
        dataSource.consultarFilas(
            BUSCAR_POR_PUBLICACION,
            { sentencia -> sentencia.setString(1, publicacionId) },
            { filas -> filas.aResumenEntity() },
        ).firstOrNull()

    /** Asigna los parámetros de la sentencia de *upsert* de resumen. */
    private fun PreparedStatement.fijarParametros(resumen: ResumenEntity) {
        setString(1, resumen.publicacionId)
        setString(2, resumen.queCambia)
        setString(3, resumen.aQuienAfecta)
        setArray(4, connection.createArrayOf("text", resumen.cifrasClave.toTypedArray()))
        setString(5, resumen.fuenteOficial)
        setBoolean(6, resumen.avisoIa)
        setString(7, resumen.estado)
        setObject(8, resumen.confianza, Types.NUMERIC)
    }

    private companion object {
        const val INSERTAR_O_ACTUALIZAR = """
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

        const val BUSCAR_POR_PUBLICACION = """
            SELECT id, publicacion_id, que_cambia, a_quien_afecta, cifras_clave,
                   fuente_oficial, aviso_ia, estado, confianza
            FROM resumen
            WHERE publicacion_id = ?
        """
    }
}
