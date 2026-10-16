package es.aviferdev.datopublico.backend.ingesta.publication

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
    fun `los ocho fixtures reales por section producen texto no vacio y metadata`() {
        EXPECTATIVAS_POR_SECCION.forEach { (name, rango) ->
            val documento = parseFixture(name)
            val texto = assertNotNull(documento.texto, "$name no produjo texto")
            assertTrue(texto.isNotBlank(), "$name produjo texto vacío")
            assertTrue(!texto.contains(ESPACIO_DURO), "$name conserva espacio duro")
            assertTrue(!texto.contains("  "), "$name conserva espacios repetidos")
            assertEquals(rango, documento.metadata.rango, "$name: rango")
            assertNotNull(documento.metadata.department, "$name: departamento")
            assertNotNull(documento.metadata.urlPdf, "$name: urlPdf")
        }
    }

    @Test
    fun `la publication extensa de la Seccion I conserva el texto y los parrafos`() {
        val documento = parseFixture("texto-I-2026-20979")
        val texto = assertNotNull(documento.texto)

        assertTrue(texto.length > 40_000, "texto truncado: ${texto.length} caracteres")
        assertTrue(texto.lines().size > 140, "pocos párrafos: ${texto.lines().size}")
        assertTrue(texto.contains("FELIPE R."), "falta el cierre de la norma")
        assertTrue(texto.contains("DIANA MORANT RIPOLL"), "falta la firma de la ministra")
        assertEquals("814/2026", documento.metadata.numeroOficial)
        assertEquals("20261007", documento.metadata.fechaDisposicion)
    }

    @Test
    fun `extrae el texto de tablas y listas de definicion de las secciones con estructura irregular`() {
        val tabla = parseFixture("texto-IIA-2024-87")
        val lista = parseFixture("texto-VA-2024-25")

        assertTrue(assertNotNull(tabla.texto).contains("ALARCON CHARLO"), "tabla II.A sin filas")
        assertTrue(assertNotNull(lista.texto).contains("Poder adjudicador"), "dl de V.A sin contenido")
    }

    @Test
    fun `normaliza el espacio duro y los espacios repetidos`() {
        val documento = "<documento><metadatos><rango>Real\u00a0Decreto</rango></metadatos>" +
            "<texto><p class=\"parrafo\">Dos  espacios\u00a0y   más</p></texto></documento>"

        val resultado = parser.parse(documento)

        assertEquals("Real Decreto", resultado.metadata.rango)
        assertEquals("Dos espacios y más", resultado.texto)
    }

    @Test
    fun `un texto vacio produce null sin excepcion`() {
        val documento = parseFixture("texto-sin-texto")

        assertNull(documento.texto)
        assertEquals("ORGANISMO SINTÉTICO", documento.metadata.department)
        assertNull(documento.metadata.rango)
    }

    @Test
    fun `un documento sin nodo texto produce null`() {
        val documento = parser.parse("<documento><metadatos><rango>Orden</rango></metadatos></documento>")

        assertNull(documento.texto)
        assertEquals("Orden", documento.metadata.rango)
    }

    @Test
    fun `un xml malformado lanza BoeTextoException`() {
        val error = assertFailsWith<BoeTextoException> {
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

        val error = assertFailsWith<BoeTextoException> { parser.parse(malicioso) }

        assertTrue(error.message!!.contains("malformado"), error.message!!)
    }

    private fun parseFixture(name: String): DocumentoBoe = parser.parse(recurso("$name.xml"))

    /** Lee un recurso de `src/test/resources/boe` como texto UTF-8. */
    private fun recurso(ruta: String): String =
        checkNotNull(javaClass.getResourceAsStream("/boe/$ruta")) { "No se encontró /boe/$ruta" }
            .readBytes()
            .decodeToString()

    private companion object {
        const val ESPACIO_DURO = '\u00a0'

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
