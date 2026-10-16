package es.aviferdev.datopublico.backend.ingesta.publicacion

import es.aviferdev.datopublico.backend.ingesta.categorizacion.DeadlineExtractor
import es.aviferdev.datopublico.backend.ingesta.categorizacion.PublicationClassifier
import es.aviferdev.datopublico.backend.ingesta.sumario.EntradaSumario

/**
 * Combinador **puro** (sin red) de una [EntradaSumario] del sumario y el
 * [DocumentoBoe] del XML en una [Publicacion].
 *
 * Es tolerante a `documento = null` (publicación sin `urlXml` o sin texto
 * disponible): produce una publicación válida con `texto = null`. `organismo`,
 * `epigrafe` y `rango` son opcionales según la sección.
 *
 * Además de mapear los campos, **clasifica** la publicación (FT00012) y **extrae
 * el plazo** de solicitud cuando la fuente lo indica de forma fiable, de modo que
 * el job diario (FT00009) y el backfill (FT00011), que reutilizan este parser,
 * obtienen `categoria` y `plazo` sin cableado extra en `Application.kt`.
 *
 * @param classifier clasificador curado y determinista (puro).
 * @param deadlineExtractor extractor heurístico y acotado del plazo (puro).
 */
class PublicacionParser(
    private val classifier: PublicationClassifier = PublicationClassifier(),
    private val deadlineExtractor: DeadlineExtractor = DeadlineExtractor(),
) {

    /**
     * Mapea [entrada] y [documento] a una [Publicacion].
     *
     * El `organismo` y la `urlPdf` del sumario tienen prioridad; si faltan, se
     * completan desde los metadatos del XML. El texto y el rango proceden del XML.
     * La `categoria` se deduce de la sección, el epígrafe y el título (siempre
     * tiene valor) y el `plazo` del texto, referido a la fecha de publicación
     * (puede ser `null`).
     *
     * @param entrada entrada del sumario del BOE (FT00006).
     * @param documento XML parseado, o `null` si no hay texto disponible.
     */
    fun parsear(entrada: EntradaSumario, documento: DocumentoBoe?): Publicacion {
        val metadatos = documento?.metadatos
        return Publicacion(
            id = entrada.identificador,
            title = entrada.title,
            publicationDate = entrada.publicationDate,
            issuingBody = entrada.issuingBody ?: metadatos?.departamento,
            section = entrada.section,
            heading = entrada.heading,
            texto = documento?.texto,
            officialUrl = entrada.officialUrl,
            urlXml = entrada.urlXml,
            urlPdf = entrada.urlPdf ?: metadatos?.urlPdf,
            rango = metadatos?.rango,
            category = classifier.classify(entrada.section, entrada.heading, entrada.title),
            deadline = deadlineExtractor.extract(documento?.texto, entrada.publicationDate),
        )
    }
}
