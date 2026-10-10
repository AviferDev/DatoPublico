package es.aviferdev.datopublico.backend.persistence

/**
 * Conversión **pura** entre un [FloatArray] y el literal textual del tipo
 * `vector` de pgvector (`[v1,v2,…]`).
 *
 * Es la pieza que permite guardar y consultar embeddings con **JDBC sin ORM**:
 * el vector viaja como texto y el SQL aplica el *cast* `?::vector`, de modo que
 * los repositorios mantienen su API sobre [FloatArray] y no dependen de
 * `org.postgresql.util.PGobject`.
 *
 * No toca red, base de datos ni estado; se prueba en el gate con
 * [PgVectorTest]. La dimensión es fija ([DIMENSIONS]) porque los vectores del
 * modelo E5 `-small` son de 384; cambiar de modelo exige migración y reindexar.
 */
object PgVector {
    /** Dimensión de los embeddings E5 (`intfloat/multilingual-e5-small`). */
    const val DIMENSIONS = 384

    /**
     * Serializa [embedding] al literal que espera pgvector (`[v1,v2,…]`).
     *
     * Usa la representación decimal de `Float` con **punto** como separador, así
     * que el resultado es **independiente del *locale*** (no aparece `,` decimal
     * aunque la máquina esté en `es_ES`).
     *
     * @param embedding vector normalizado del modelo (384 dims).
     * @return literal listo para enlazar con `?::vector`.
     * @throws IllegalArgumentException si [embedding] no tiene [DIMENSIONS].
     */
    fun toLiteral(embedding: FloatArray): String {
        require(embedding.size == DIMENSIONS) {
            "El embedding debe tener $DIMENSIONS dimensiones, pero tiene ${embedding.size}."
        }
        return embedding.joinToString(separator = ",", prefix = "[", postfix = "]")
    }

    /**
     * Interpreta el literal de pgvector [literal].
     *
     * @param literal texto como `[v1,v2,…]` leído de la columna `embedding`.
     * @return el vector, o `null` si [literal] es `null` o está en blanco.
     * @throws IllegalArgumentException si el literal tiene un valor no numérico o
     *   una dimensión distinta de [DIMENSIONS].
     */
    fun parse(literal: String?): FloatArray? =
        literal?.trim()?.takeIf { text -> text.isNotEmpty() }?.let { text -> parseLiteral(text) }

    /** Convierte un literal no vacío `[v1,v2,…]` en su vector de 384 dims. */
    private fun parseLiteral(literal: String): FloatArray {
        val body = literal.removePrefix("[").removeSuffix("]")
        val values = body.split(",").map { token -> parseComponent(token.trim()) }
        require(values.size == DIMENSIONS) {
            "El literal del embedding debe tener $DIMENSIONS dimensiones, pero tiene ${values.size}."
        }
        return values.toFloatArray()
    }

    /** Convierte un componente textual en `Float`, fallando en claro si no lo es. */
    private fun parseComponent(token: String): Float {
        val value = token.toFloatOrNull()
        require(value != null) {
            "El literal del embedding contiene un valor no numérico: '$token'."
        }
        return value
    }
}
