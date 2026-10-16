package es.aviferdev.datopublico.backend.ingestion.publication

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parser del XML estructurado del BOE con los **fixtures reales** de las ocho
 * secciones (sin red).
 *
 * Cubre los escenarios 1–3 del spec a nivel de XML: campos por sección, la
 * publicación extensa, la ausencia de texto, la normalización y la seguridad
 * (XML malformado y DTD/entidades externas).
 */
class BoeXmlParserTest {

    private val parser = BoeXmlParser()

    @Test
    fun `los ocho fixtures reales por section producen body no vacio y metadata`() {
        EXPECTATIVAS_POR_SECCION.forEach { (name, rank) ->
            val document = parseFixture(name)
            val body = assertNotNull(document.body, "$name no produjo texto")
            assertTrue(body.isNotBlank(), "$name produjo texto vacío")
            assertTrue(!body.contains(HARD_SPACE), "$name conserva espacio duro")
            assertTrue(!body.contains("  "), "$name conserva espacios repetidos")
            assertEquals(rank, document.metadata.rank, "$name: rango")
            assertNotNull(document.metadata.department, "$name: departamento")
            assertNotNull(document.metadata.pdfUrl, "$name: urlPdf")
        }
    }

    @Test
    fun `la publication extensa de la Seccion I conserva el body y los parrafos`() {
        val document = parseFixture("texto-I-2026-20979")
        val body = assertNotNull(document.body)

        assertTrue(body.length > 40_000, "texto truncado: ${body.length} caracteres")
        assertTrue(body.lines().size > 140, "pocos párrafos: ${body.lines().size}")
        assertTrue(body.contains("FELIPE R."), "falta el cierre de la norma")
        assertTrue(body.contains("DIANA MORANT RIPOLL"), "falta la firma de la ministra")
        assertEquals("814/2026", document.metadata.officialNumber)
        assertEquals("20261007", document.metadata.dispositionDate)
    }

    @Test
    fun `extrae el body de tablas y listas de definicion de las secciones con estructura irregular`() {
        val tabla = parseFixture("texto-IIA-2024-87")
        val lista = parseFixture("texto-VA-2024-25")

        assertTrue(assertNotNull(tabla.body).contains("ALARCON CHARLO"), "tabla II.A sin filas")
        assertTrue(assertNotNull(lista.body).contains("Poder adjudicador"), "dl de V.A sin contenido")
    }

    @Test
    fun `normaliza el espacio duro y los espacios repetidos`() {
        val document = "<documento><metadatos><rango>Real\u00a0Decreto</rango></metadatos>" +
            "<texto><p class=\"parrafo\">Dos  espacios\u00a0y   más</p></texto></documento>"

        val resultado = parser.parse(document)

        assertEquals("Real Decreto", resultado.metadata.rank)
        assertEquals("Dos espacios y más", resultado.body)
    }

    @Test
    fun `un body vacio produce null sin excepcion`() {
        val document = parseFixture("texto-sin-texto")

        assertNull(document.body)
        assertEquals("ORGANISMO SINTÉTICO", document.metadata.department)
        assertNull(document.metadata.rank)
    }

    @Test
    fun `un document sin node body produce null`() {
        val document = parser.parse("<documento><metadatos><rango>Orden</rango></metadatos></documento>")

        assertNull(document.body)
        assertEquals("Orden", document.metadata.rank)
    }

    @Test
    fun `un xml malformado lanza BoeTextException`() {
        val error = assertFailsWith<BoeTextException> {
            parser.parse("<documento><texto><p class=\"parrafo\">sin cerrar")
        }

        assertTrue(error.message!!.contains("malformado"), error.message!!)
    }

    @Test
    fun `un xml con doctype o entidades externas se rechaza por seguridad`() {
        val malicioso = """
            <?xml version="1.0"?>
            <!DOCTYPE documento [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <documento><metadatos><rango>&xxe;</rango></metadatos><texto/></documento>
        """.trimIndent()

        val error = assertFailsWith<BoeTextException> { parser.parse(malicioso) }

        assertTrue(error.message!!.contains("malformado"), error.message!!)
    }

    private fun parseFixture(name: String): BoeDocument = parser.parse(recurso("$name.xml"))

    /** Lee un recurso de `src/test/resources/boe` como texto UTF-8. */
    private fun recurso(ruta: String): String =
        checkNotNull(javaClass.getResourceAsStream("/boe/$ruta")) { "No se encontró /boe/$ruta" }
            .readBytes()
            .decodeToString()

    private companion object {
        const val HARD_SPACE = '\u00a0'

        /** Rango normativo esperado por fixture; `null` en las secciones sin rango. */
        val EXPECTATIVAS_POR_SECCION: Map<String, String?> = mapOf(
            "texto-I-2026-20979" to "Real Decreto",
            "texto-IIA-2024-87" to "Resolución",
            "texto-IIB-2024-93" to "Resolución",
            "texto-III-2024-117" to "Orden",
            "texto-IV-2024-1" to null,
            "texto-VA-2024-25" to null,
            "texto-VB-2024-76" to null,
            "texto-VC-2024-92" to null,
        )
    }
}
