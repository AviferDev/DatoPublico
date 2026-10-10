package es.aviferdev.datopublico.backend.persistencia

import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * Persistencia **mínima** de fragmentos de una publicación (tabla `fragmento`).
 *
 * El modelo de dominio y el comportamiento real del chunking llegan con FT00014;
 * aquí solo se guardan y se leen por publicación. La columna de embedding y su
 * índice HNSW son de FT00016 y **no** están en esta tabla todavía.
 */
interface FragmentoRepositorio {
    /**
     * Inserta o actualiza el fragmento por `(publicacion_id, orden)` y devuelve
     * el modelo persistido con su `id`.
     */
    fun guardar(fragmento: FragmentoEntity): FragmentoEntity

    /** Lista los fragmentos de [publicacionId] ordenados por `orden`. */
    fun listarPorPublicacion(publicacionId: String): List<FragmentoEntity>
}

/**
 * Fila de la tabla `fragmento` (representación de persistencia provisional).
 *
 * [id] es `null` mientras el fragmento no se ha insertado; [orden] es la
 * posición dentro de la publicación (única por publicación).
 */
data class FragmentoEntity(
    val id: Long? = null,
    val publicacionId: String,
    val orden: Int,
    val referencia: String?,
    val contenido: String,
)

/** Lee la fila actual del [ResultSet] como [FragmentoEntity]. */
internal fun ResultSet.aFragmentoEntity(): FragmentoEntity = FragmentoEntity(
    id = getLong("id"),
    publicacionId = getString("publicacion_id"),
    orden = getInt("orden"),
    referencia = getString("referencia"),
    contenido = getString("contenido"),
)

/**
 * Implementación JDBC de [FragmentoRepositorio] sobre un [DataSource].
 *
 * Cada método abre y cierra su conexión (autocommit); el pool lo gestiona
 * [es.aviferdev.datopublico.backend.infra.BaseDatos].
 */
class FragmentoRepositorioJdbc(private val dataSource: DataSource) : FragmentoRepositorio {

    override fun guardar(fragmento: FragmentoEntity): FragmentoEntity {
        val id = dataSource.consultarFilas(
            INSERTAR_O_ACTUALIZAR,
            { sentencia -> sentencia.fijarParametros(fragmento) },
            { filas -> filas.getLong("id") },
        ).first()
        return fragmento.copy(id = id)
    }

    override fun listarPorPublicacion(publicacionId: String): List<FragmentoEntity> =
        dataSource.consultarFilas(
            LISTAR_POR_PUBLICACION,
            { sentencia -> sentencia.setString(1, publicacionId) },
            { filas -> filas.aFragmentoEntity() },
        )

    /** Asigna los parámetros de la sentencia de *upsert* de fragmento. */
    private fun PreparedStatement.fijarParametros(fragmento: FragmentoEntity) {
        setString(1, fragmento.publicacionId)
        setInt(2, fragmento.orden)
        setString(3, fragmento.referencia)
        setString(4, fragmento.contenido)
    }

    private companion object {
        const val INSERTAR_O_ACTUALIZAR = """
            INSERT INTO fragmento (publicacion_id, orden, referencia, contenido)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (publicacion_id, orden) DO UPDATE SET
                referencia = EXCLUDED.referencia,
                contenido = EXCLUDED.contenido
            RETURNING id
        """

        const val LISTAR_POR_PUBLICACION = """
            SELECT id, publicacion_id, orden, referencia, contenido
            FROM fragmento
            WHERE publicacion_id = ?
            ORDER BY orden
        """
    }
}
