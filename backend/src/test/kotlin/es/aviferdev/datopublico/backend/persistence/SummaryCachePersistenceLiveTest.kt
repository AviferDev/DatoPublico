package es.aviferdev.datopublico.backend.persistence

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.SummaryDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import java.io.File
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prueba **opt-in** contra el PostgreSQL real de `docker-compose` (escenario 8 del
 * spec): *round-trip* de la caché del resumen, *upsert* idempotente por
 * `cache_key` y agregado del consumo de tokens.
 *
 * Se salta por defecto (`DB_LIVE_TEST != 1`) para que el gate y la CI **no**
 * dependan de una base de datos. Requiere el esquema migrado (migración `V4`,
 * `./gradlew :backend:flywayMigrate`) y una `.env` o el entorno con `POSTGRES_*`.
 * Para ejecutarla:
 *
 * ```
 * DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*SummaryCachePersistenceLiveTest'
 * ```
 */
class SummaryCachePersistenceLiveTest {

    @Test
    fun `persists the cache and the token usage round trip`() {
        if (System.getenv("DB_LIVE_TEST") != "1") {
            println("SummaryCachePersistenceLiveTest omitido: exporta DB_LIVE_TEST=1 para el test real.")
        } else {
            val dataSource = Database.createDataSource(DatabaseConfig.fromEnv(environment()))
            try {
                clean(dataSource)
                try {
                    PublicationRepositoryJdbc(dataSource).save(publication())
                    checkCacheRoundTrip(dataSource)
                    checkCacheUpsertIsIdempotent(dataSource)
                    checkUsageAggregate(dataSource)
                } finally {
                    clean(dataSource)
                }
            } finally {
                (dataSource as AutoCloseable).close()
            }
        }
    }

    /** Escenario: guardar y leer la caché devuelve el mismo `SummaryDto`. */
    private fun checkCacheRoundTrip(dataSource: DataSource) {
        val repository = SummaryCacheRepositoryJdbc(dataSource)
        repository.put(cacheEntity(whatChanges = "Resumen cacheado"))

        val retrieved = repository.find(CACHE_KEY)

        assertEquals(CACHE_KEY, retrieved?.cacheKey)
        assertEquals(PUBLICATION_ID, retrieved?.publicationId)
        assertEquals(summary(whatChanges = "Resumen cacheado"), retrieved?.summary)
        println("SummaryCachePersistenceLiveTest: cache round-trip OK (cacheKey=$CACHE_KEY)")
    }

    /** Escenario: reescribir la misma clave actualiza y **no** duplica la fila. */
    private fun checkCacheUpsertIsIdempotent(dataSource: DataSource) {
        val repository = SummaryCacheRepositoryJdbc(dataSource)
        repository.put(cacheEntity(whatChanges = "Resumen actualizado"))

        val retrieved = repository.find(CACHE_KEY)

        assertEquals(summary(whatChanges = "Resumen actualizado"), retrieved?.summary)
        assertEquals(1L, dataSource.cacheRowCount(CACHE_KEY))
        println("SummaryCachePersistenceLiveTest: upsert idempotente OK (filas=1)")
    }

    /** Escenario: los tokens registrados cuadran con el agregado `totalTokens()`. */
    private fun checkUsageAggregate(dataSource: DataSource) {
        val repository = GenerationUsageRepositoryJdbc(dataSource)
        val before = repository.totalTokens()
        repository.record(usageEntity(cacheHit = true, promptTokens = 0, completionTokens = 0, totalTokens = 0))
        repository.record(usageEntity(cacheHit = false, promptTokens = 11, completionTokens = 22, totalTokens = 33))

        val records = repository.listByPublication(PUBLICATION_ID)

        assertEquals(2, records.size)
        assertTrue(repository.totalTokens() - before == 33L, "el agregado debe sumar 33 tokens")
        println("SummaryCachePersistenceLiveTest: uso OK (registros=${records.size}, delta=${repository.totalTokens() - before})")
    }

    private fun cacheEntity(whatChanges: String): SummaryCacheEntity = SummaryCacheEntity(
        cacheKey = CACHE_KEY,
        publicationId = PUBLICATION_ID,
        model = MODEL,
        promptVersion = PROMPT_VERSION,
        summary = summary(whatChanges),
    )

    private fun summary(whatChanges: String): SummaryDto = SummaryDto(
        whatChanges = whatChanges,
        whoIsAffected = "A la ciudadanía",
        keyFigures = listOf("30 días"),
        officialSource = "https://www.boe.es/diario_boe/txt.php?id=$PUBLICATION_ID",
        aiNotice = true,
    )

    private fun usageEntity(
        cacheHit: Boolean,
        promptTokens: Int,
        completionTokens: Int,
        totalTokens: Int,
    ): GenerationUsageEntity = GenerationUsageEntity(
        publicationId = PUBLICATION_ID,
        cacheKey = CACHE_KEY,
        model = MODEL,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens,
        cacheHit = cacheHit,
    )

    private fun publication(): Publicacion = Publicacion(
        id = PUBLICATION_ID,
        title = "Resolución de prueba de caché de resumen",
        publicationDate = DATE,
        issuingBody = null,
        section = SeccionBoeDto.II_A,
        heading = null,
        texto = null,
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=$PUBLICATION_ID",
        urlXml = null,
        urlPdf = null,
        rango = null,
        category = CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS,
    )

    /** Borra la publicación (cascada limpia la caché) y el log de uso (sin FK). */
    private fun clean(dataSource: DataSource) {
        dataSource.executeUpdate("DELETE FROM publicacion WHERE id = ?") { statement ->
            statement.setString(1, PUBLICATION_ID)
        }
        dataSource.executeUpdate("DELETE FROM generacion_uso WHERE publicacion_id = ?") { statement ->
            statement.setString(1, PUBLICATION_ID)
        }
    }

    private fun DataSource.cacheRowCount(cacheKey: String): Long =
        queryRows(
            "SELECT count(*) AS count FROM resumen_cache WHERE cache_key = ?",
            { statement -> statement.setString(1, cacheKey) },
            { rows -> rows.getLong("count") },
        ).first()

    /**
     * Entorno del test: variables del proceso más, si falta alguna, las de la
     * `.env` de la raíz del producto (sin exportar).
     */
    private fun environment(): Map<String, String> {
        val environment = System.getenv().toMutableMap()
        val dotEnv = listOf(File(".env"), File("../.env")).firstOrNull { file -> file.isFile }
        dotEnv?.readLines()
            ?.asSequence()
            ?.map { line -> line.trim() }
            ?.filter { line -> line.isNotEmpty() && !line.startsWith("#") && line.contains('=') }
            ?.forEach { line ->
                val key = line.substringBefore('=').trim()
                environment.putIfAbsent(key, line.substringAfter('=').trim().trim('"', '\''))
            }
        return environment
    }

    private companion object {
        const val DATE = "2026-10-09"
        const val PUBLICATION_ID = "FT00021-LIVE-CACHE"
        const val MODEL = "deepseek-v4.1-flash"
        const val PROMPT_VERSION = "v1"
        const val CACHE_KEY = "$PUBLICATION_ID|$MODEL|$PROMPT_VERSION"
    }
}
