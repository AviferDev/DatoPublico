package es.aviferdev.datopublico.backend.ingesta.publicacion

import es.aviferdev.datopublico.backend.ingesta.sumario.EntradaSumario

/**
 * Combinador **puro** (sin red) de una [EntradaSumario] del sumario y el
 * [DocumentoBoe] del XML en una [Publicacion].
 *
 * Es tolerante a `documento = null` (publicación sin `urlXml` o sin texto
 * disponible): produce una publicación válida con `texto = null`. `organismo`,
 * `epigrafe` y `rango` son opcionales según la sección; `categoria` queda `null`
 * (pendiente de FT00012).
 */
class PublicacionParser {

    /**
     * Mapea [entrada] y [documento] a una [Publicacion].
     *
     * El `organismo` y la `urlPdf` del sumario tienen prioridad; si faltan, se
     * completan desde los metadatos del XML. El texto y el rango proceden del XML.
     *
     * @param entrada entrada del sumario del BOE (FT00006).
     * @param documento XML parseado, o `null` si no hay texto disponible.
     */
    fun parsear(entrada: EntradaSumario, documento: DocumentoBoe?): Publicacion {
        val metadatos = documento?.metadatos
        return Publicacion(
            id = entrada.identificador,
            titulo = entrada.titulo,
            fechaPublicacion = entrada.fechaPublicacion,
            organismo = entrada.organismo ?: metadatos?.departamento,
            seccion = entrada.seccion,
            epigrafe = entrada.epigrafe,
            texto = documento?.texto,
            urlOficial = entrada.urlOficial,
            urlXml = entrada.urlXml,
            urlPdf = entrada.urlPdf ?: metadatos?.urlPdf,
            rango = metadatos?.rango,
        )
    }
}
