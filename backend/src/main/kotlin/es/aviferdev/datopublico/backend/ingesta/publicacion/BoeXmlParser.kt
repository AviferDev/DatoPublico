package es.aviferdev.datopublico.backend.ingesta.publicacion

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource

/**
 * Parser puro del XML estructurado de una publicación del BOE (`xml.php`).
 *
 * Extrae los metadatos del bloque `<metadatos>` y el texto de `<texto>`,
 * normalizando espacios. **No** accede a la red: recibe el XML ya descargado.
 *
 * **Seguridad (MUST)**: el XML es contenido remoto no confiable y se parsea con
 * *secure processing*, **sin DTD y sin entidades externas**
 * (`disallow-doctype-decl`, `ACCESS_EXTERNAL_DTD`/`SCHEMA` vacíos, sin expandir
 * referencias). Así se mitigan XXE (*XML External Entity*) y *billion laughs*.
 *
 * El texto se compone concatenando los bloques de párrafo (`p`, `li`, `dt`, `dd`)
 * con saltos de línea; los contenedores (`texto`, `dl`, `table`, celdas…) se
 * recorren recursivamente. Se normalizan el espacio duro `\u00a0` y los espacios
 * repetidos. `<texto>` ausente o vacío → [DocumentoBoe.texto] `null`.
 *
 * @throws BoeTextoException si el XML está malformado o contiene DTD/entidades.
 */
class BoeXmlParser {

    /**
     * Parsea [xml] y devuelve el [DocumentoBoe] con metadatos y texto.
     *
     * @throws BoeTextoException si el XML es ilegible o intenta usar DTD/entidades.
     */
    fun parse(xml: String): DocumentoBoe = try {
        val documento = documentoDe(xml)
        DocumentoBoe(
            texto = textoDe(documento),
            metadatos = metadatosDe(documento),
        )
    } catch (error: Exception) {
        throw BoeTextoException("XML del BOE malformado o no permitido (DTD/entidades externas)", error)
    }

    /** Construye el DOM con *secure processing* y deshabilitando DTD/entidades. */
    private fun documentoDe(xml: String): Document =
        documentoBuilderFactory().newDocumentBuilder().parse(InputSource(StringReader(xml)))

    /** Factoría del DOM endurecida: sin DTD, sin entidades externas, sin expansión. */
    private fun documentoBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature(DISALLOW_DOCTYPE_DECL, true)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            isExpandEntityReferences = false
        }

    /** Extrae los metadatos del `<metadatos>` raíz; cada campo es `null` si falta. */
    private fun metadatosDe(documento: Document): MetadatosBoe {
        val metadatos = hijoDirecto(documento.documentElement, "metadatos")
        return MetadatosBoe(
            rango = textoHijo(metadatos, "rango"),
            departamento = textoHijo(metadatos, "departamento"),
            fechaDisposicion = textoHijo(metadatos, "fecha_disposicion"),
            numeroOficial = textoHijo(metadatos, "numero_oficial"),
            urlPdf = textoHijo(metadatos, "url_pdf"),
            origenLegislativo = textoHijo(metadatos, "origen_legislativo"),
        )
    }

    /** Compone el texto del `<texto>` raíz por líneas; `null` si queda vacío o no existe. */
    private fun textoDe(documento: Document): String? =
        lineasDe(hijoDirecto(documento.documentElement, "texto"))
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .takeIf { it.isNotEmpty() }

    /** Texto normalizado del primer descendiente [etiqueta] de [padre]; `null` si vacío. */
    private fun textoHijo(padre: Element?, etiqueta: String): String? =
        textoNormalizado(padre?.getElementsByTagName(etiqueta)?.item(0)?.textContent.orEmpty())
            .takeIf { it.isNotEmpty() }

    /**
     * Primer hijo **directo** de [padre] con el nombre [etiqueta], o `null`.
     *
     * Importante: no vale `getElementsByTagName`, porque `<texto>` y otros nodos
     * también aparecen anidados dentro de `<analisis>` y se elegiría el equivocado.
     */
    private fun hijoDirecto(padre: Element?, etiqueta: String): Element? =
        padre?.let { elemento ->
            (0 until elemento.childNodes.length)
                .mapNotNull { elemento.childNodes.item(it) as? Element }
                .firstOrNull { it.tagName == etiqueta }
        }

    /** Líneas de texto de los nodos hijos de [elemento] (vacío si [elemento] es `null`). */
    private fun lineasDe(elemento: Element?): List<String> =
        elemento?.let { lineasDeNodos(it.childNodes) }.orEmpty()

    /** Recolecta las líneas de todos los nodos hijos de [nodos]. */
    private fun lineasDeNodos(nodos: NodeList): List<String> =
        (0 until nodos.length).flatMap { lineasDeNodo(nodos.item(it)) }

    /** Una sola línea para un nodo de texto, recursión para un elemento, nada para el resto. */
    private fun lineasDeNodo(nodo: Node): List<String> = when (nodo.nodeType) {
        Node.ELEMENT_NODE -> lineasDeElemento(nodo as Element)
        Node.TEXT_NODE -> listOf(textoNormalizado(nodo.nodeValue.orEmpty())).filter { it.isNotEmpty() }
        else -> emptyList()
    }

    /** Un bloque de párrafo es una línea; un contenedor (tabla, `dl`…) se recorre. */
    private fun lineasDeElemento(elemento: Element): List<String> =
        if (esUnidadDeTexto(elemento)) {
            listOf(textoNormalizado(elemento.textContent)).filter { it.isNotEmpty() }
        } else {
            lineasDe(elemento)
        }

    /** Es bloque de línea si es `p`/`li`/`dt`/`dd` y no contiene otro bloque anidado. */
    private fun esUnidadDeTexto(elemento: Element): Boolean =
        elemento.tagName in ETIQUETAS_UNIDAD && !tieneDescendienteUnidad(elemento)

    /** ¿Contiene [elemento] algún bloque de línea anidado (`p`/`li`/`dt`/`dd`)? */
    private fun tieneDescendienteUnidad(elemento: Element): Boolean =
        ETIQUETAS_UNIDAD.any { elemento.getElementsByTagName(it).length > 0 }

    /** Normaliza el espacio duro a espacio, colapsa repetidos y recorta los extremos. */
    private fun textoNormalizado(texto: String): String =
        texto.replace(ESPACIO_DURO, ' ').replace(ESPACIOS, " ").trim()

    private companion object {
        /** Feature de Xerces/JDK que rechaza cualquier DTD (`DOCTYPE`). */
        const val DISALLOW_DOCTYPE_DECL = "http://apache.org/xml/features/disallow-doctype-decl"

        /** Espacio duro (`NBSP`) que el BOE intercala en encabezados y fechas. */
        const val ESPACIO_DURO = '\u00a0'

        val ESPACIOS = Regex("\\s+")

        /** Etiquetas cuyo contenido forma una línea de párrafo. */
        val ETIQUETAS_UNIDAD = setOf("p", "li", "dt", "dd")
    }
}
