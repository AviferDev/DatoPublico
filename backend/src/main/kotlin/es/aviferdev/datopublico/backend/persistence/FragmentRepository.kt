package es.aviferdev.datopublico.backend.persistence

import es.aviferdev.datopublico.backend.rag.Fragment
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * Persistencia de fragmentos de una publicación (tabla `fragmento`).
 *
 * El modelo de dominio y el chunking por artículo ya existen (FT00014); aquí se
 * guardan, se leen por publicación y, desde FT00016, se **almacenan** sus
 * embeddings (columna `embedding vector(384)` con índice HNSW coseno) y se
 * consultan por **similitud** (los `k` vecinos más cercanos). Sin filtros de
 * metadatos: la recuperación híbrida es de FT00017.
 */
interface FragmentRepository {
    /**
     * Inserta o actualiza el fragmento por `(publicacion_id, orden)` y devuelve
     * el modelo persistido con su `id`.
     */
    fun save(fragment: FragmentEntity): FragmentEntity

    /** Lista los fragmentos de [publicationId] ordenados por `orden`. */
    fun listByPublication(publicationId: String): List<FragmentEntity>

    /**
     * Guarda o actualiza el embedding del fragmento `(publicationId, order)`.
     *
     * Requiere que la fila exista (la crea [save]); si no existe, **no** escribe
     * nada y devuelve `false`. El vector se enlaza como literal con *cast*
     * `?::vector` ([PgVector.toLiteral]).
     *
     * @param embedding vector normalizado de [PgVector.DIMENSIONS] dimensiones.
     * @return `true` si actualizó una fila; `false` si no había fragmento.
     * @throws IllegalArgumentException si [embedding] no tiene la dimensión
     *   esperada (fail-fast **antes** de tocar la base de datos).
     */
    fun saveEmbedding(publicationId: String, order: Int, embedding: FloatArray): Boolean

    /**
     * Devuelve los [limit] fragmentos más cercanos a [queryEmbedding] por
     * **distancia coseno** (menor = más parecido), excluyendo los que no tienen
     * embedding (`embedding IS NOT NULL`).
     *
     * @param queryEmbedding vector de consulta normalizado de
     *   [PgVector.DIMENSIONS] dimensiones.
     * @param limit número de vecinos a devolver (`>= 1`).
     * @return coincidencias ordenadas por distancia ascendente.
     * @throws IllegalArgumentException si [queryEmbedding] no tiene la dimensión
     *   esperada o `limit < 1` (fail-fast **antes** de tocar la base de datos).
     */
    fun findNearest(queryEmbedding: FloatArray, limit: Int): List<FragmentMatch>
}

/** Fragmento recuperado por similitud, con su distancia coseno (menor = mejor). */
data class FragmentMatch(val fragment: FragmentEntity, val distance: Double)

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
 * Mapea el [Fragment] de dominio (capa RAG, FT00014) a su entidad de
 * persistencia [FragmentEntity].
 *
 * Extensión **pura**: solo cierra el encaje de tipos (la capa de datos depende del
 * dominio, dirección correcta); **no** abre conexión ni invoca el repositorio. El
 * `id` queda `null` hasta que [FragmentRepository.save] inserte la fila.
 *
 * @return entidad lista para persistir, con `id = null`.
 */
fun Fragment.toEntity(): FragmentEntity = FragmentEntity(
    id = null,
    publicationId = publicationId,
    order = order,
    reference = reference,
    content = content,
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

    override fun saveEmbedding(publicationId: String, order: Int, embedding: FloatArray): Boolean {
        val literal = PgVector.toLiteral(embedding)
        val updated = dataSource.queryRows(
            UPDATE_EMBEDDING,
            { statement -> statement.bindEmbedding(publicationId, order, literal) },
            { rows -> rows.getLong("id") },
        )
        return updated.isNotEmpty()
    }

    override fun findNearest(queryEmbedding: FloatArray, limit: Int): List<FragmentMatch> {
        require(limit >= 1) { "El límite de vecinos debe ser >= 1, pero es $limit." }
        val literal = PgVector.toLiteral(queryEmbedding)
        return dataSource.queryRows(
            FIND_NEAREST,
            { statement -> statement.bindNearest(literal, limit) },
            { rows -> rows.toFragmentMatch() },
        )
    }

    /** Asigna los parámetros de la sentencia de *upsert* de fragmento. */
    private fun PreparedStatement.bindParameters(fragment: FragmentEntity) {
        setString(1, fragment.publicationId)
        setInt(2, fragment.order)
        setString(3, fragment.reference)
        setString(4, fragment.content)
    }

    /** Asigna el literal del embedding y la clave `(publicacion_id, orden)`. */
    private fun PreparedStatement.bindEmbedding(publicationId: String, order: Int, literal: String) {
        setString(1, literal)
        setString(2, publicationId)
        setInt(3, order)
    }

    /** Asigna el literal de consulta (dos veces: `distance` y `ORDER BY`) y el límite. */
    private fun PreparedStatement.bindNearest(literal: String, limit: Int) {
        setString(1, literal)
        setString(2, literal)
        setInt(3, limit)
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

        const val UPDATE_EMBEDDING = """
            UPDATE fragmento SET embedding = ?::vector
            WHERE publicacion_id = ? AND orden = ?
            RETURNING id
        """

        const val FIND_NEAREST = """
            SELECT id, publicacion_id, orden, referencia, contenido,
                   embedding <=> ?::vector AS distance
            FROM fragmento
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> ?::vector
            LIMIT ?
        """
    }
}

/** Lee la fila actual del [ResultSet] como [FragmentMatch] (fragmento + distancia). */
private fun ResultSet.toFragmentMatch(): FragmentMatch = FragmentMatch(
    fragment = toFragmentEntity(),
    distance = getDouble("distance"),
)
