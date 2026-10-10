package es.aviferdev.datopublico.backend.ingesta.publicacion

/**
 * Resultado del parseo del XML estructurado de una publicación del BOE
 * (`https://www.boe.es/diario_boe/xml.php?id=<identificador>`).
 *
 * Es un modelo **interno** de `:backend` (ni serializable ni `Dto`). Separa el
 * **texto** ya normalizado de los **metadatos** del `<documento>` para que el
 * [PublicacionParser] y la persistencia (FT00008) los usen por separado.
 *
 * [texto] es `null` cuando la publicación no trae `<texto>` o lo trae vacío
 * (documentos servidos solo como imagen/PDF): no se inventa texto.
 */
data class DocumentoBoe(
    /** Texto completo normalizado por párrafos, o `null` si no hay texto. */
    val texto: String?,
    /** Metadatos del bloque `<metadatos>` del XML. */
    val metadatos: MetadatosBoe,
)

/**
 * Metadatos del bloque `<metadatos>` del XML del BOE que no vienen en el sumario.
 *
 * Todos los campos son **opcionales** porque la estructura varía entre secciones
 * (las Secciones IV, V.A, V.B y V.C no traen rango ni origen legislativo, y el
 * `numero_oficial` puede venir vacío).
 */
data class MetadatosBoe(
    /** Rango normativo (`Real Decreto`, `Orden`, `Resolución`…). */
    val rango: String?,
    /** Departamento emisor (organismo). */
    val departamento: String?,
    /** Fecha de disposición en formato `YYYYMMDD` tal cual la publica el BOE. */
    val fechaDisposicion: String?,
    /** Número oficial (`814/2026`, `DEF/1431/2023`…); opcional. */
    val numeroOficial: String?,
    /** URL del PDF oficial. */
    val urlPdf: String?,
    /** Origen legislativo (`Estatal`, `Autonómico`…); opcional. */
    val origenLegislativo: String?,
)
