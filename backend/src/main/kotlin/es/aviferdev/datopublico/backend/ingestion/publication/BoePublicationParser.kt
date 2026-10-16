package es.aviferdev.datopublico.backend.ingestion.publication

import es.aviferdev.datopublico.backend.ingestion.summary.SummaryEntry

/**
 * Punto de entrada de la ingesta de una publicación: descarga su texto y lo
 * combina con la entrada del sumario.
 *
 * Es lo que consumirán la persistencia (FT00008) y el job diario (FT00009). Si
 * la entrada no trae `urlXml`, **no** se hace ninguna petición y la publicación
 * se produce con `texto = null`.
 */
interface BoePublicationParser {
    /**
     * Descarga (si procede) el texto de [entry] y devuelve la [Publication].
     *
     * @throws BoeTextException si el texto no se puede obtener o parsear.
     */
    suspend fun parse(entry: SummaryEntry): Publication
}

/**
 * Implementación de [BoePublicationParser] que orquesta el [BoeTextClient] de
 * red y el [PublicationParser] puro.
 *
 * @param textoClient cliente del XML estructurado del BOE.
 * @param publicacionParser combinador puro sumario + documento → publicación.
 */
class BoePublicationHttpParser(
    private val textClient: BoeTextClient,
    private val publicationParser: PublicationParser = PublicationParser(),
) : BoePublicationParser {

    override suspend fun parse(entry: SummaryEntry): Publication {
        val document = entry.xmlUrl?.let { textClient.fetchDocument(it) }
        return publicationParser.parse(entry, document)
    }
}
