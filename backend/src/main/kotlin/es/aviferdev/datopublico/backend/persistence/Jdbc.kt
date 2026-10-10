package es.aviferdev.datopublico.backend.persistence

import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * Helpers JDBC compartidos por los repositorios de `persistence/`.
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
 * @param bind asigna los parámetros de la [PreparedStatement].
 */
internal fun DataSource.executeUpdate(
    sql: String,
    bind: (PreparedStatement) -> Unit,
) {
    connection.use { dbConnection ->
        dbConnection.prepareStatement(sql).use { statement ->
            bind(statement)
            statement.executeUpdate()
        }
    }
}

/**
 * Ejecuta una consulta parametrizada y mapea cada fila con [mapRow].
 *
 * @param sql consulta SQL con parámetros (`?`).
 * @param bind asigna los parámetros de la [PreparedStatement].
 * @param mapRow convierte cada [ResultSet] (posicionado en su fila) en `T`.
 */
internal fun <T> DataSource.queryRows(
    sql: String,
    bind: (PreparedStatement) -> Unit,
    mapRow: (ResultSet) -> T,
): List<T> =
    connection.use { dbConnection ->
        dbConnection.prepareStatement(sql).use { statement ->
            bind(statement)
            statement.executeQuery().use { rows -> rows.mapAll(mapRow) }
        }
    }

/**
 * Recorre las filas de un [ResultSet] y las mapea a una lista.
 *
 * @param mapRow conversión de la fila actual a `T`.
 */
private fun <T> ResultSet.mapAll(mapRow: (ResultSet) -> T): List<T> {
    val results = mutableListOf<T>()
    while (next()) {
        results.add(mapRow(this))
    }
    return results
}
