package es.aviferdev.datopublico.backend.ingestion.publication

/**
 * Resultado del parseo del XML estructurado de una publicación del BOE
 * (`https://www.boe.es/diario_boe/xml.php?id=<identificador>`).
 *
 * Es un modelo **interno** de `:backend` (ni serializable ni `Dto`). Separa el
 * **texto** ya normalizado de los **metadatos** del `<documento>` para que el
 * [PublicationParser] y la persistencia (FT00008) los usen por separado.
 *
 * [body] es `null` cuando la publicación no trae `<texto>` o lo trae vacío
 * (documentos servidos solo como imagen/PDF): no se inventa texto.
 */
data class BoeDocument(
    /** Texto completo normalizado por párrafos, o `null` si no hay texto. */
    val body: String?,
    /** Metadatos del bloque `<metadatos>` del XML. */
    val metadata: BoeMetadata,
)

/**
 * Metadatos del bloque `<metadatos>` del XML del BOE que no vienen en el sumario.
 *
 * Todos los campos son **opcionales** porque la estructura varía entre secciones
 * (las Secciones IV, V.A, V.B y V.C no traen rango ni origen legislativo, y el
 * `numero_oficial` puede venir vacío).
 */
data class BoeMetadata(
    /** Rango normativo (`Real Decreto`, `Orden`, `Resolución`…). */
    val rank: String?,
    /** Departamento emisor (organismo). */
    val department: String?,
    /** Fecha de disposición en formato `YYYYMMDD` tal cual la publica el BOE. */
    val dispositionDate: String?,
    /** Número oficial (`814/2026`, `DEF/1431/2023`…); opcional. */
    val officialNumber: String?,
    /** URL del PDF oficial. */
    val pdfUrl: String?,
    /** Origen legislativo (`Estatal`, `Autonómico`…); opcional. */
    val legislativeOrigin: String?,
)
