package es.aviferdev.datopublico.backend.ingestion.publication

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
 * repetidos. `<texto>` ausente o vacío → [BoeDocument.body] `null`.
 *
 * @throws BoeTextException si el XML está malformado o contiene DTD/entidades.
 */
class BoeXmlParser {

    /**
     * Parsea [xml] y devuelve el [BoeDocument] con metadatos y texto.
     *
     * @throws BoeTextException si el XML es ilegible o intenta usar DTD/entidades.
     */
    fun parse(xml: String): BoeDocument = try {
        val document = documentOf(xml)
        BoeDocument(
            body = textOf(document),
            metadata = metadataOf(document),
        )
    } catch (error: Exception) {
        throw BoeTextException("XML del BOE malformado o no permitido (DTD/entidades externas)", error)
    }

    /** Construye el DOM con *secure processing* y deshabilitando DTD/entidades. */
    private fun documentOf(xml: String): Document =
        documentBuilderFactory().newDocumentBuilder().parse(InputSource(StringReader(xml)))

    /** Factoría del DOM endurecida: sin DTD, sin entidades externas, sin expansión. */
    private fun documentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature(DISALLOW_DOCTYPE_DECL, true)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            isExpandEntityReferences = false
        }

    /** Extrae los metadatos del `<metadatos>` raíz; cada campo es `null` si falta. */
    private fun metadataOf(document: Document): BoeMetadata {
        val metadata = directChild(document.documentElement, "metadatos")
        return BoeMetadata(
            rank = childText(metadata, "rango"),
            department = childText(metadata, "departamento"),
            dispositionDate = childText(metadata, "fecha_disposicion"),
            officialNumber = childText(metadata, "numero_oficial"),
            pdfUrl = childText(metadata, "url_pdf"),
            legislativeOrigin = childText(metadata, "origen_legislativo"),
        )
    }

    /** Compone el texto del `<texto>` raíz por líneas; `null` si queda vacío o no existe. */
    private fun textOf(document: Document): String? =
        linesOf(directChild(document.documentElement, "texto"))
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .takeIf { it.isNotEmpty() }

    /** Texto normalizado del primer descendiente [tag] de [parent]; `null` si vacío. */
    private fun childText(parent: Element?, tag: String): String? =
        normalizedText(parent?.getElementsByTagName(tag)?.item(0)?.textContent.orEmpty())
            .takeIf { it.isNotEmpty() }

    /**
     * Primer hijo **directo** de [parent] con el nombre [tag], o `null`.
     *
     * Importante: no vale `getElementsByTagName`, porque `<texto>` y otros nodos
     * también aparecen anidados dentro de `<analisis>` y se elegiría el equivocado.
     */
    private fun directChild(parent: Element?, tag: String): Element? =
        parent?.let { element ->
            (0 until element.childNodes.length)
                .mapNotNull { element.childNodes.item(it) as? Element }
                .firstOrNull { it.tagName == tag }
        }

    /** Líneas de texto de los nodos hijos de [element] (vacío si [element] es `null`). */
    private fun linesOf(element: Element?): List<String> =
        element?.let { linesOfNodes(it.childNodes) }.orEmpty()

    /** Recolecta las líneas de todos los nodos hijos de [nodes]. */
    private fun linesOfNodes(nodes: NodeList): List<String> =
        (0 until nodes.length).flatMap { linesOfNode(nodes.item(it)) }

    /** Una sola línea para un nodo de texto, recursión para un elemento, nada para el resto. */
    private fun linesOfNode(node: Node): List<String> = when (node.nodeType) {
        Node.ELEMENT_NODE -> linesOfElement(node as Element)
        Node.TEXT_NODE -> listOf(normalizedText(node.nodeValue.orEmpty())).filter { it.isNotEmpty() }
        else -> emptyList()
    }

    /** Un bloque de párrafo es una línea; un contenedor (tabla, `dl`…) se recorre. */
    private fun linesOfElement(element: Element): List<String> =
        if (isTextUnit(element)) {
            listOf(normalizedText(element.textContent)).filter { it.isNotEmpty() }
        } else {
            linesOf(element)
        }

    /** Es bloque de línea si es `p`/`li`/`dt`/`dd` y no contiene otro bloque anidado. */
    private fun isTextUnit(element: Element): Boolean =
        element.tagName in TEXT_UNIT_TAGS && !hasUnitDescendant(element)

    /** ¿Contiene [element] algún bloque de línea anidado (`p`/`li`/`dt`/`dd`)? */
    private fun hasUnitDescendant(element: Element): Boolean =
        TEXT_UNIT_TAGS.any { element.getElementsByTagName(it).length > 0 }

    /** Normaliza el espacio duro a espacio, colapsa repetidos y recorta los extremos. */
    private fun normalizedText(body: String): String =
        body.replace(HARD_SPACE, ' ').replace(WHITESPACE, " ").trim()

    private companion object {
        /** Feature de Xerces/JDK que rechaza cualquier DTD (`DOCTYPE`). */
        const val DISALLOW_DOCTYPE_DECL = "http://apache.org/xml/features/disallow-doctype-decl"

        /** Espacio duro (`NBSP`) que el BOE intercala en encabezados y fechas. */
        const val HARD_SPACE = '\u00a0'

        val WHITESPACE = Regex("\\s+")

        /** Etiquetas cuyo contenido forma una línea de párrafo. */
        val TEXT_UNIT_TAGS = setOf("p", "li", "dt", "dd")
    }
}
