package es.aviferdev.datopublico.backend.ingesta.publicacion

import es.aviferdev.datopublico.backend.ingesta.sumario.EntradaSumario

/**
 * Punto de entrada de la ingesta de una publicación: descarga su texto y lo
 * combina con la entrada del sumario.
 *
 * Es lo que consumirán la persistencia (FT00008) y el job diario (FT00009). Si
 * la entrada no trae `urlXml`, **no** se hace ninguna petición y la publicación
 * se produce con `texto = null`.
 */
interface BoePublicacionParser {
    /**
     * Descarga (si procede) el texto de [entrada] y devuelve la [Publicacion].
     *
     * @throws BoeTextoException si el texto no se puede obtener o parsear.
     */
    suspend fun parsear(entrada: EntradaSumario): Publicacion
}

/**
 * Implementación de [BoePublicacionParser] que orquesta el [BoeTextoClient] de
 * red y el [PublicacionParser] puro.
 *
 * @param textoClient cliente del XML estructurado del BOE.
 * @param publicacionParser combinador puro sumario + documento → publicación.
 */
class BoePublicacionHttpParser(
    private val textoClient: BoeTextoClient,
    private val publicacionParser: PublicacionParser = PublicacionParser(),
) : BoePublicacionParser {

    override suspend fun parsear(entrada: EntradaSumario): Publicacion {
        val documento = entrada.urlXml?.let { textoClient.obtenerDocumento(it) }
        return publicacionParser.parsear(entrada, documento)
    }
}
