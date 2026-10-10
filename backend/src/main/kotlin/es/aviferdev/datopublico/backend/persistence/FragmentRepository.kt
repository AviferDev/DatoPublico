package es.aviferdev.datopublico.backend.persistence

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
interface FragmentRepository {
    /**
     * Inserta o actualiza el fragmento por `(publicacion_id, orden)` y devuelve
     * el modelo persistido con su `id`.
     */
    fun save(fragment: FragmentEntity): FragmentEntity

    /** Lista los fragmentos de [publicationId] ordenados por `orden`. */
    fun listByPublication(publicationId: String): List<FragmentEntity>
}

/**
 * Fila de la tabla `fragmento` (representación de persistencia provisional).
 *
 * [id] es `null` mientras el fragmento no se ha insertado; [order] es la
 * posición dentro de la publicación (única por publicación).
 */
data class FragmentEntity(
    val id: Long? = null,
    val publicationId: String,
    val order: Int,
    val reference: String?,
    val content: String,
)

/** Lee la fila actual del [ResultSet] como [FragmentEntity]. */
internal fun ResultSet.toFragmentEntity(): FragmentEntity = FragmentEntity(
    id = getLong("id"),
    publicationId = getString("publicacion_id"),
    order = getInt("orden"),
    reference = getString("referencia"),
    content = getString("contenido"),
)

/**
 * Implementación JDBC de [FragmentRepository] sobre un [DataSource].
 *
 * Cada método abre y cierra su conexión (autocommit); el pool lo gestiona
 * [es.aviferdev.datopublico.backend.infra.Database].
 */
class FragmentRepositoryJdbc(private val dataSource: DataSource) : FragmentRepository {

    override fun save(fragment: FragmentEntity): FragmentEntity {
        val id = dataSource.queryRows(
            UPSERT,
            { statement -> statement.bindParameters(fragment) },
            { rows -> rows.getLong("id") },
        ).first()
        return fragment.copy(id = id)
    }

    override fun listByPublication(publicationId: String): List<FragmentEntity> =
        dataSource.queryRows(
            LIST_BY_PUBLICATION,
            { statement -> statement.setString(1, publicationId) },
            { rows -> rows.toFragmentEntity() },
        )

    /** Asigna los parámetros de la sentencia de *upsert* de fragmento. */
    private fun PreparedStatement.bindParameters(fragment: FragmentEntity) {
        setString(1, fragment.publicationId)
        setInt(2, fragment.order)
        setString(3, fragment.reference)
        setString(4, fragment.content)
    }

    private companion object {
        const val UPSERT = """
            INSERT INTO fragmento (publicacion_id, orden, referencia, contenido)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (publicacion_id, orden) DO UPDATE SET
                referencia = EXCLUDED.referencia,
                contenido = EXCLUDED.contenido
            RETURNING id
        """

        const val LIST_BY_PUBLICATION = """
            SELECT id, publicacion_id, orden, referencia, contenido
            FROM fragmento
            WHERE publicacion_id = ?
            ORDER BY orden
        """
    }
}
