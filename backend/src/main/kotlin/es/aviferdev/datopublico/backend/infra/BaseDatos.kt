package es.aviferdev.datopublico.backend.infra

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

/**
 * Fábrica del pool de conexiones JDBC ([HikariDataSource]) que usan los
 * repositorios de `persistencia/`.
 *
 * **No** se invoca en el arranque del servidor (la composición no cablea la base
 * de datos): el consumidor (un job, un test o una feature posterior) es quien
 * crea el pool y **lo cierra** al terminar. El tamaño del pool es pequeño a
 * propósito (servidor Intel N100 / 8 GB); los métodos de los repositorios son
 * bloqueantes y deben invocarse fuera del hilo de eventos HTTP.
 */
object BaseDatos {
    /** Tamaño máximo del pool (servidor N100, carga baja). */
    const val TAMANO_MAXIMO_POOL = 5

    /** Nombre del pool en las métricas/logs de HikariCP. */
    const val NOMBRE_POOL = "datopublico"

    private const val DRIVER_POSTGRESQL = "org.postgresql.Driver"

    /**
     * Construye un [DataSource] con pool sobre [configuracion].
     *
     * @param configuracion conexión derivada de `POSTGRES_*`
     *   ([ConfiguracionBd.fromEnv]).
     * @return pool Hikari listo para inyectar en los repositorios; el llamante
     *   es responsable de cerrarlo.
     */
    fun crearDataSource(configuracion: ConfiguracionBd): DataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = configuracion.url
            username = configuracion.usuario
            password = configuracion.password
            driverClassName = DRIVER_POSTGRESQL
            maximumPoolSize = TAMANO_MAXIMO_POOL
            poolName = NOMBRE_POOL
        }
        return HikariDataSource(hikariConfig)
    }
}
