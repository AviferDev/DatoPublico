package es.aviferdev.datopublico.backend.persistence

import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * Registro del **consumo de tokens** por generación (tabla `generacion_uso`,
 * FT00021).
 *
 * Cada generación (acierto de caché o llamada al proveedor) deja una fila con los
 * tokens (prompt/completion/total) y si fue un acierto (`cache_hit`). No tiene FK
 * a `publicacion`: es un **log** de coste que no debe impedir borrar una
 * publicación. Solo registra tokens, no tarifas.
 */
interface GenerationUsageRepository {
    /**
     * Inserta un registro de uso y devuelve la fila persistida con su `id`.
     *
     * @param usage registro a persistir (`id` se ignora).
     */
    fun record(usage: GenerationUsageEntity): GenerationUsageEntity

    /** Lista los registros de uso de [publicationId] en orden de inserción. */
    fun listByPublication(publicationId: String): List<GenerationUsageEntity>

    /** Suma los tokens totales de todos los registros (`0` si no hay ninguno). */
    fun totalTokens(): Long
}

/**
 * Fila de la tabla `generacion_uso`.
 *
 * [id] es `null` mientras no se ha insertado; [cacheHit] distingue un acierto de
 * caché (0 tokens) de una generación real.
 */
data class GenerationUsageEntity(
    val id: Long? = null,
    val publicationId: String,
    val cacheKey: String,
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val cacheHit: Boolean,
)

/** Lee la fila actual del [ResultSet] como [GenerationUsageEntity]. */
internal fun ResultSet.toGenerationUsageEntity(): GenerationUsageEntity = GenerationUsageEntity(
    id = getLong("id"),
    publicationId = getString("publicacion_id"),
    cacheKey = getString("cache_key"),
    model = getString("modelo"),
    promptTokens = getInt("prompt_tokens"),
    completionTokens = getInt("completion_tokens"),
    totalTokens = getInt("total_tokens"),
    cacheHit = getBoolean("cache_hit"),
)

/**
 * Implementación JDBC de [GenerationUsageRepository] sobre un [DataSource].
 *
 * Cada método abre y cierra su conexión (autocommit); el pool lo gestiona
 * [es.aviferdev.datopublico.backend.infra.Database].
 */
class GenerationUsageRepositoryJdbc(private val dataSource: DataSource) : GenerationUsageRepository {

    override fun record(usage: GenerationUsageEntity): GenerationUsageEntity {
        val id = dataSource.queryRows(
            INSERT,
            { statement -> statement.bindParameters(usage) },
            { rows -> rows.getLong("id") },
        ).first()
        return usage.copy(id = id)
    }

    override fun listByPublication(publicationId: String): List<GenerationUsageEntity> =
        dataSource.queryRows(
            LIST_BY_PUBLICATION,
            { statement -> statement.setString(1, publicationId) },
            { rows -> rows.toGenerationUsageEntity() },
        )

    override fun totalTokens(): Long =
        dataSource.queryRows(
            TOTAL_TOKENS,
            { },
            { rows -> rows.getLong("total_tokens") },
        ).first()

    /** Asigna los siete parámetros de la sentencia de inserción de uso. */
    private fun PreparedStatement.bindParameters(usage: GenerationUsageEntity) {
        setString(1, usage.publicationId)
        setString(2, usage.cacheKey)
        setString(3, usage.model)
        setInt(4, usage.promptTokens)
        setInt(5, usage.completionTokens)
        setInt(6, usage.totalTokens)
        setBoolean(7, usage.cacheHit)
    }

    private companion object {
        const val INSERT = """
            INSERT INTO generacion_uso (
                publicacion_id, cache_key, modelo, prompt_tokens,
                completion_tokens, total_tokens, cache_hit
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING id
        """

        const val LIST_BY_PUBLICATION = """
            SELECT id, publicacion_id, cache_key, modelo, prompt_tokens,
                   completion_tokens, total_tokens, cache_hit
            FROM generacion_uso
            WHERE publicacion_id = ?
            ORDER BY id
        """

        const val TOTAL_TOKENS = """
            SELECT COALESCE(SUM(total_tokens), 0) AS total_tokens
            FROM generacion_uso
        """
    }
}
