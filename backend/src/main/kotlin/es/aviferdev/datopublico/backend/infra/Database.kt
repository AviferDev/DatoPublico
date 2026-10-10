package es.aviferdev.datopublico.backend.infra

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

/**
 * Fábrica del pool de conexiones JDBC ([HikariDataSource]) que usan los
 * repositorios de `persistence/`.
 *
 * **No** se invoca en el arranque del servidor (la composición no cablea la base
 * de datos): el consumidor (un job, un test o una feature posterior) es quien
 * crea el pool y **lo cierra** al terminar. Los métodos de los repositorios son
 * bloqueantes y deben invocarse fuera del hilo de eventos HTTP.
 */
object Database {
    /** Tamaño máximo del pool. */
    const val MAX_POOL_SIZE = 5

    /** Nombre del pool en las métricas/logs de HikariCP. */
    const val POOL_NAME = "datopublico"

    private const val POSTGRESQL_DRIVER = "org.postgresql.Driver"

    /**
     * Construye un [DataSource] con pool sobre [config].
     *
     * @param config conexión derivada de `POSTGRES_*`
     *   ([DatabaseConfig.fromEnv]).
     * @return pool Hikari listo para inyectar en los repositorios; el llamante
     *   es responsable de cerrarlo.
     */
    fun createDataSource(config: DatabaseConfig): DataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            driverClassName = POSTGRESQL_DRIVER
            maximumPoolSize = MAX_POOL_SIZE
            poolName = POOL_NAME
        }
        return HikariDataSource(hikariConfig)
    }
}
