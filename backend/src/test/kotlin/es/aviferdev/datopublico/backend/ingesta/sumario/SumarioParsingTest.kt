package es.aviferdev.datopublico.backend.ingesta.sumario

import es.aviferdev.datopublico.model.SeccionBoeDto
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parseo de los fixtures **reales** del sumario del BOE (sin red).
 *
 * `20261009` incluye la Sección V.A y `item` en ambas formas; `20240102` incluye
 * las Secciones IV (Justicia) y V.C (envoltura `departamento.texto.item`) y
 * `departamento` como objeto. Ambos se parsean sin descartar ninguna sección y su
 * **unión** cubre las ocho del BOE (test explícito `SeccionBoeDto.entries`).
 */
class SumarioParsingTest {

    @Test
    fun `fixture real 20261009 devuelve todas las secciones presentes`() {
        val entradas = parse("sumario-20261009.json", LocalDate.of(2026, 10, 9))

        assertEquals(
            setOf(
                SeccionBoeDto.I,
                SeccionBoeDto.II_A,
                SeccionBoeDto.II_B,
                SeccionBoeDto.III,
                SeccionBoeDto.V_A,
                SeccionBoeDto.V_B,
            ),
            entradas.map { it.seccion }.toSet(),
        )
        assertEquals(237, entradas.size, "1:5 + 2A:16 + 2B:31 + 3:69 + 5A:32 + 5B:84")
        // La Sección V.A del fixture ahora se incluye.
        assertTrue(entradas.any { it.identificador == "BOE-B-2026-32742" })
        assertTrue(entradas.all { it.fechaPublicacion == "2026-10-09" })
    }

    @Test
    fun `fixture real 20261009 conserva los campos de una entrada de la Seccion I`() {
        val entrada = parse("sumario-20261009.json", LocalDate.of(2026, 10, 9))
            .first { it.identificador == "BOE-A-2026-20976" }

        assertEquals(SeccionBoeDto.I, entrada.seccion)
        assertEquals("2026/15317", entrada.control)
        assertEquals("MINISTERIO DE ASUNTOS EXTERIORES, UNIÓN EUROPEA Y COOPERACIÓN", entrada.organismo)
        assertEquals("Acuerdos internacionales administrativos", entrada.epigrafe)
        assertEquals("https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-20976", entrada.urlOficial)
        assertEquals("https://www.boe.es/diario_boe/xml.php?id=BOE-A-2026-20976", entrada.urlXml)
        assertEquals(
            "https://www.boe.es/boe/dias/2026/10/09/pdfs/BOE-A-2026-20976.pdf",
            entrada.urlPdf,
        )
        assertTrue(entrada.titulo.startsWith("Acuerdo internacional administrativo"))
    }

    @Test
    fun `fixture real 20240102 con item y departamento en forma de objeto se parsea`() {
        val entradas = parse("sumario-20240102.json", LocalDate.of(2024, 1, 2))

        assertEquals(
            setOf(
                SeccionBoeDto.II_A,
                SeccionBoeDto.II_B,
                SeccionBoeDto.III,
                SeccionBoeDto.IV,
                SeccionBoeDto.V_A,
                SeccionBoeDto.V_B,
                SeccionBoeDto.V_C,
            ),
            entradas.map { it.seccion }.toSet(),
        )
        assertEquals(143, entradas.size, "2A:6 + 2B:24 + 3:18 + 4:24 + 5A:51 + 5B:16 + 5C:4")
        // Las Secciones IV y V.C llegan bajo `departamento.texto.item`.
        assertTrue(entradas.any { it.identificador == "BOE-B-2024-1" })
        assertTrue(entradas.any { it.identificador == "BOE-B-2024-92" })
        // La Sección V.A del fixture ahora se incluye.
        assertTrue(entradas.any { it.identificador == "BOE-B-2024-25" })
        assertTrue(entradas.any { it.identificador == "BOE-B-2024-76" })
    }

    @Test
    fun `la envoltura texto de la Seccion IV produce entradas del modelo interno`() {
        val entrada = parse("sumario-20240102.json", LocalDate.of(2024, 1, 2))
            .first { it.identificador == "BOE-B-2024-1" }

        assertEquals(SeccionBoeDto.IV, entrada.seccion)
        assertEquals("ALMERIA", entrada.titulo)
        assertEquals("JUZGADOS DE PRIMERA INSTANCIA E INSTRUCCIÓN", entrada.organismo)
        assertNull(entrada.control)
        assertNull(entrada.epigrafe)
        assertEquals("https://www.boe.es/diario_boe/txt.php?id=BOE-B-2024-1", entrada.urlOficial)
        assertEquals(
            "https://www.boe.es/boe/dias/2024/01/02/pdfs/BOE-B-2024-1.pdf",
            entrada.urlPdf,
        )
    }

    @Test
    fun `la union de las secciones de ambos fixtures cubre las ocho del BOE`() {
        val secciones = listOf(
            parse("sumario-20261009.json", LocalDate.of(2026, 10, 9)),
            parse("sumario-20240102.json", LocalDate.of(2024, 1, 2)),
        ).flatMap { entradas -> entradas.map { it.seccion } }.toSet()

        assertEquals(
            SeccionBoeDto.entries.toSet(),
            secciones,
            "Ningún fixture trae las 8 secciones por separado; su unión debe cubrirlas.",
        )
    }

    private fun parse(fixture: String, fecha: LocalDate): List<EntradaSumario> {
        val texto = checkNotNull(javaClass.getResourceAsStream("/boe/$fixture")) {
            "No se encontró el fixture /boe/$fixture"
        }.readBytes().decodeToString()
        val response = DatoPublicoJson.decodeFromString<BoeSumarioResponseDto>(texto)
        return response.toEntradasSumario(fecha)
    }
}
