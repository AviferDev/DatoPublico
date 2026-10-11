package es.aviferdev.datopublico.backend.persistence

import es.aviferdev.datopublico.model.ResumenDto
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * Caché del **resumen ciudadano** por clave determinista (tabla `resumen_cache`,
 * FT00021).
 *
 * Es una tabla **propia y separada** de `resumen` (que guarda el resumen
 * publicado): aquí se cachea el `ResumenDto` serializado por
 * `publicationId + modelo + versión del prompt`. La escritura es un *upsert*
 * idempotente por `cache_key`; la lectura devuelve el resumen cacheado o `null`.
 */
interface SummaryCacheRepository {
    /**
     * Devuelve la entrada cacheada con [cacheKey], o `null` si no existe.
     *
     * @param cacheKey clave determinista (publicación + modelo + prompt).
     */
    fun find(cacheKey: String): SummaryCacheEntity?

    /**
     * Inserta o actualiza la entrada por su `cache_key` (*upsert* idempotente).
     *
     * @param entity entrada a persistir; requiere que la publicación exista
     *   (FK a `publicacion`).
     * @return la entrada persistida (misma que [entity]).
     */
    fun put(entity: SummaryCacheEntity): SummaryCacheEntity
}

/**
 * Fila de la tabla `resumen_cache`.
 *
 * [summary] es el contrato [ResumenDto] completo; se guarda serializado a JSON en
 * la columna `resumen` (ver [encodeSummaryCache]/[decodeSummaryCache]).
 */
data class SummaryCacheEntity(
    val cacheKey: String,
    val publicationId: String,
    val model: String,
    val promptVersion: String,
    val summary: ResumenDto,
)

/**
 * Serializa [summary] al JSON que se guarda en la caché (codec **puro**).
 *
 * Reutiliza [DatoPublicoJson] (misma configuración que el resto del proyecto) y
 * no toca red ni BD.
 */
internal fun encodeSummaryCache(summary: ResumenDto): String =
    DatoPublicoJson.encodeToString(ResumenDto.serializer(), summary)

/**
 * Deserializa el JSON de la columna `resumen` al contrato [ResumenDto].
 *
 * @throws kotlinx.serialization.SerializationException si el JSON no es un
 *   `ResumenDto` válido (dato corrupto: fail-fast).
 */
internal fun decodeSummaryCache(json: String): ResumenDto =
    DatoPublicoJson.decodeFromString(ResumenDto.serializer(), json)

/** Lee la fila actual del [ResultSet] como [SummaryCacheEntity]. */
internal fun ResultSet.toSummaryCacheEntity(): SummaryCacheEntity = SummaryCacheEntity(
    cacheKey = getString("cache_key"),
    publicationId = getString("publicacion_id"),
    model = getString("modelo"),
    promptVersion = getString("prompt_version"),
    summary = decodeSummaryCache(getString("resumen")),
)

/**
 * Implementación JDBC de [SummaryCacheRepository] sobre un [DataSource].
 *
 * Cada método abre y cierra su conexión (autocommit); el pool lo gestiona
 * [es.aviferdev.datopublico.backend.infra.Database].
 */
class SummaryCacheRepositoryJdbc(private val dataSource: DataSource) : SummaryCacheRepository {

    override fun find(cacheKey: String): SummaryCacheEntity? =
        dataSource.queryRows(
            FIND_BY_KEY,
            { statement -> statement.setString(1, cacheKey) },
            { rows -> rows.toSummaryCacheEntity() },
        ).firstOrNull()

    override fun put(entity: SummaryCacheEntity): SummaryCacheEntity {
        dataSource.executeUpdate(UPSERT) { statement -> statement.bindParameters(entity) }
        return entity
    }

    /** Asigna los cinco parámetros de la sentencia de *upsert* de caché. */
    private fun PreparedStatement.bindParameters(entity: SummaryCacheEntity) {
        setString(1, entity.cacheKey)
        setString(2, entity.publicationId)
        setString(3, entity.model)
        setString(4, entity.promptVersion)
        setString(5, encodeSummaryCache(entity.summary))
    }

    private companion object {
        const val UPSERT = """
            INSERT INTO resumen_cache (cache_key, publicacion_id, modelo, prompt_version, resumen)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (cache_key) DO UPDATE SET
                publicacion_id = EXCLUDED.publicacion_id,
                modelo = EXCLUDED.modelo,
                prompt_version = EXCLUDED.prompt_version,
                resumen = EXCLUDED.resumen
        """

        const val FIND_BY_KEY = """
            SELECT cache_key, publicacion_id, modelo, prompt_version, resumen
            FROM resumen_cache
            WHERE cache_key = ?
        """
    }
}
