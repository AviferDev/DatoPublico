package es.aviferdev.datopublico.backend.persistencia

import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * Helpers JDBC compartidos por los repositorios de `persistencia/`.
 *
 * Centralizan el manejo de conexiones (`use`), sentencias parametrizadas y el
 * mapeo de filas para que cada repositorio declare solo su SQL y su modelo. No
 * hay ORM ni *unit of work*: cada método abre su conexión en autocommit y la
 * cierra.
 */

/**
 * Ejecuta una sentencia de escritura parametrizada sobre [DataSource].
 *
 * @param sql sentencia SQL con parámetros (`?`).
 * @param parametrizar asigna los parámetros de la [PreparedStatement].
 */
internal fun DataSource.ejecutarActualizacion(
    sql: String,
    parametrizar: (PreparedStatement) -> Unit,
) {
    connection.use { conexion ->
        conexion.prepareStatement(sql).use { sentencia ->
            parametrizar(sentencia)
            sentencia.executeUpdate()
        }
    }
}

/**
 * Ejecuta una consulta parametrizada y mapea cada fila con [mapear].
 *
 * @param sql consulta SQL con parámetros (`?`).
 * @param parametrizar asigna los parámetros de la [PreparedStatement].
 * @param mapear convierte cada [ResultSet] (posicionado en su fila) en `T`.
 */
internal fun <T> DataSource.consultarFilas(
    sql: String,
    parametrizar: (PreparedStatement) -> Unit,
    mapear: (ResultSet) -> T,
): List<T> =
    connection.use { conexion ->
        conexion.prepareStatement(sql).use { sentencia ->
            parametrizar(sentencia)
            sentencia.executeQuery().use { filas -> filas.mapearTodas(mapear) }
        }
    }

/**
 * Recorre las filas de un [ResultSet] y las mapea a una lista.
 *
 * @param mapear conversión de la fila actual a `T`.
 */
private fun <T> ResultSet.mapearTodas(mapear: (ResultSet) -> T): List<T> {
    val resultado = mutableListOf<T>()
    while (next()) {
        resultado.add(mapear(this))
    }
    return resultado
}
