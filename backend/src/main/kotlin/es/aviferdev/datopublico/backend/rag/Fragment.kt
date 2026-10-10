package es.aviferdev.datopublico.backend.rag

/**
 * Fragmento de dominio de una
 * [es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion], listo para
 * vectorizar (FT00015) y persistir (FT00016+).
 *
 * Es el modelo **interno** de la capa RAG (ni serializable, ni `Entity`, ni `Ui`),
 * por eso va **sin sufijo** (convención de `CONSTRAINTS.md` §«Nombres y
 * contratos»). **No** lleva `id`: lo asigna la persistencia al insertar la fila en
 * la tabla `fragmento`
 * ([es.aviferdev.datopublico.backend.persistence.FragmentEntity]);
 * [es.aviferdev.datopublico.backend.persistence.toEntity] cierra ese mapeo.
 *
 * Invariantes que garantiza
 * [es.aviferdev.datopublico.backend.rag.chunking.ArticleChunker]:
 * - [order] es **contiguo** `0..n-1` en orden de documento (clave natural
 *   `(publicacion_id, orden)` en la tabla `fragmento`).
 * - [content] nunca está en blanco y respeta el presupuesto de tokens del modelo
 *   E5 (`<= 512` con la estimación provisional de FT00014).
 * - [reference] conserva el encabezado de artículo/disposición o la sección
 *   cuando el texto no tiene encabezados.
 *
 * @property publicationId identificador oficial del BOE de la publicación origen.
 * @property order posición del fragmento dentro de la publicación.
 * @property reference referencia legible (encabezado o sección); el mapeo la
 *   conserva tal cual aunque el tipo admita `null`.
 * @property content texto del fragmento: una o más líneas del texto de origen.
 */
data class Fragment(
    val publicationId: String,
    val order: Int,
    val reference: String?,
    val content: String,
)
