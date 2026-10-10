package es.aviferdev.datopublico.backend.rag.retrieval

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Traduce un [SearchFilter] a la **cláusula SQL** y a los **parámetros** de una
 * consulta de vecinos más cercanos, sobre el alias `p` de la tabla `publicacion`.
 *
 * Es **puro** y testeable sin base de datos: no abre conexiones, no concatena
 * entrada de usuario (todo va parametrizado con `?`) y no conoce el repositorio.
 * Lo consume [es.aviferdev.datopublico.backend.persistence.FragmentRepositoryJdbc]
 * al construir su `JOIN publicacion`.
 *
 * **Orden documentado y coherente** entre las dos funciones (mismo orden que los
 * `?` de la cláusula): `publishedFrom`, `publishedTo`, `category`, `section`,
 * `organization`. Las fechas se validan con [LocalDate.parse] y se enlazan como
 * **texto con *cast* `?::date`** (mismo patrón que `?::vector`), así que todos los
 * parámetros son `String`.
 *
 * Las columnas comparadas son las del esquema `V2` (`fecha_publicacion`,
 * `categoria`, `seccion`, `organismo`), donde categoría y sección se guardan por
 * el **nombre del enum** Kotlin (`CategoriaDto.name`, `SeccionBoeDto.name`).
 */
object SearchFilterSql {

    /**
     * Devuelve la cláusula ` AND …` con *placeholders* `?` para [filter], o `""`
     * si el filtro no tiene ningún campo.
     *
     * @throws IllegalArgumentException si una fecha de [filter] no es ISO-8601
     *   (fail-fast **antes** de tocar la base de datos).
     */
    fun whereClause(filter: SearchFilter): String =
        conditions(filter).joinToString(separator = "") { condition -> " AND ${condition.sql}" }

    /**
     * Devuelve los valores enlazados en el **mismo orden** que los `?` de
     * [whereClause].
     *
     * @throws IllegalArgumentException si una fecha de [filter] no es ISO-8601
     *   (fail-fast **antes** de tocar la base de datos).
     */
    fun parameters(filter: SearchFilter): List<String> =
        conditions(filter).map { condition -> condition.value }

    /**
     * Condiciones del filtro en el orden documentado, cada una con su fragmento
     * SQL y su valor enlazado. Única fuente de verdad del orden.
     */
    private fun conditions(filter: SearchFilter): List<Condition> = buildList {
        filter.publishedFrom?.let { value -> add(Condition("p.fecha_publicacion >= ?::date", parseDate(value))) }
        filter.publishedTo?.let { value -> add(Condition("p.fecha_publicacion <= ?::date", parseDate(value))) }
        filter.category?.let { value -> add(Condition("p.categoria = ?", value.name)) }
        filter.section?.let { value -> add(Condition("p.seccion = ?", value.name)) }
        filter.organization?.let { value -> add(Condition("p.organismo = ?", value)) }
    }

    /**
     * Valida que [value] es una fecha ISO-8601 y la devuelve tal cual.
     *
     * @throws IllegalArgumentException si [value] no parsea como `LocalDate`.
     */
    private fun parseDate(value: String): String {
        try {
            LocalDate.parse(value)
        } catch (exception: DateTimeParseException) {
            throw IllegalArgumentException("Fecha de filtro no ISO-8601: '$value'.", exception)
        }
        return value
    }

    /** Condición del filtro: fragmento SQL con su `?` y el valor enlazado. */
    private data class Condition(val sql: String, val value: String)
}
