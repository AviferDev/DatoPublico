package es.aviferdev.datopublico.backend.ingestion.publication

import es.aviferdev.datopublico.backend.ingesta.categorizacion.DeadlineExtractor
import es.aviferdev.datopublico.backend.ingesta.categorizacion.PublicationClassifier
import es.aviferdev.datopublico.backend.ingestion.summary.SummaryEntry

/**
 * Combinador **puro** (sin red) de una [SummaryEntry] del sumario y el
 * [BoeDocument] del XML en una [Publication].
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
class PublicationParser(
    private val classifier: PublicationClassifier = PublicationClassifier(),
    private val deadlineExtractor: DeadlineExtractor = DeadlineExtractor(),
) {

    /**
     * Mapea [entry] y [document] a una [Publication].
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
    fun parse(entry: SummaryEntry, document: BoeDocument?): Publication {
        val metadata = document?.metadata
        return Publication(
            id = entry.identifier,
            title = entry.title,
            publicationDate = entry.publicationDate,
            issuingBody = entry.issuingBody ?: metadata?.department,
            section = entry.section,
            heading = entry.heading,
            body = document?.body,
            officialUrl = entry.officialUrl,
            xmlUrl = entry.xmlUrl,
            pdfUrl = entry.pdfUrl ?: metadata?.pdfUrl,
            rank = metadata?.rank,
            category = classifier.classify(entry.section, entry.heading, entry.title),
            deadline = deadlineExtractor.extract(document?.body, entry.publicationDate),
        )
    }
}
