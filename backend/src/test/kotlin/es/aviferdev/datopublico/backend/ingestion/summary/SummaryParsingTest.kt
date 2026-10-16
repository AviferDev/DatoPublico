package es.aviferdev.datopublico.backend.ingestion.summary

import es.aviferdev.datopublico.model.BoeSectionDto
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
 * **unión** cubre las ocho del BOE (test explícito `BoeSectionDto.entries`).
 */
class SummaryParsingTest {

    @Test
    fun `fixture real 20261009 devuelve todas las secciones presentes`() {
        val entries = parse("sumario-20261009.json", LocalDate.of(2026, 10, 9))

        assertEquals(
            setOf(
                BoeSectionDto.I,
                BoeSectionDto.II_A,
                BoeSectionDto.II_B,
                BoeSectionDto.III,
                BoeSectionDto.V_A,
                BoeSectionDto.V_B,
            ),
            entries.map { it.section }.toSet(),
        )
        assertEquals(237, entries.size, "1:5 + 2A:16 + 2B:31 + 3:69 + 5A:32 + 5B:84")
        // La Sección V.A del fixture ahora se incluye.
        assertTrue(entries.any { it.identifier == "BOE-B-2026-32742" })
        assertTrue(entries.all { it.publicationDate == "2026-10-09" })
    }

    @Test
    fun `fixture real 20261009 conserva los campos de una entry de la Seccion I`() {
        val entry = parse("sumario-20261009.json", LocalDate.of(2026, 10, 9))
            .first { it.identifier == "BOE-A-2026-20976" }

        assertEquals(BoeSectionDto.I, entry.section)
        assertEquals("2026/15317", entry.control)
        assertEquals("MINISTERIO DE ASUNTOS EXTERIORES, UNIÓN EUROPEA Y COOPERACIÓN", entry.issuingBody)
        assertEquals("Acuerdos internacionales administrativos", entry.heading)
        assertEquals("https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-20976", entry.officialUrl)
        assertEquals("https://www.boe.es/diario_boe/xml.php?id=BOE-A-2026-20976", entry.xmlUrl)
        assertEquals(
            "https://www.boe.es/boe/dias/2026/10/09/pdfs/BOE-A-2026-20976.pdf",
            entry.pdfUrl,
        )
        assertTrue(entry.title.startsWith("Acuerdo internacional administrativo"))
    }

    @Test
    fun `fixture real 20240102 con item y department en forma de objeto se parsea`() {
        val entries = parse("sumario-20240102.json", LocalDate.of(2024, 1, 2))

        assertEquals(
            setOf(
                BoeSectionDto.II_A,
                BoeSectionDto.II_B,
                BoeSectionDto.III,
                BoeSectionDto.IV,
                BoeSectionDto.V_A,
                BoeSectionDto.V_B,
                BoeSectionDto.V_C,
            ),
            entries.map { it.section }.toSet(),
        )
        assertEquals(143, entries.size, "2A:6 + 2B:24 + 3:18 + 4:24 + 5A:51 + 5B:16 + 5C:4")
        // Las Secciones IV y V.C llegan bajo `departamento.texto.item`.
        assertTrue(entries.any { it.identifier == "BOE-B-2024-1" })
        assertTrue(entries.any { it.identifier == "BOE-B-2024-92" })
        // La Sección V.A del fixture ahora se incluye.
        assertTrue(entries.any { it.identifier == "BOE-B-2024-25" })
        assertTrue(entries.any { it.identifier == "BOE-B-2024-76" })
    }

    @Test
    fun `la envoltura text de la Seccion IV produce entries del modelo interno`() {
        val entry = parse("sumario-20240102.json", LocalDate.of(2024, 1, 2))
            .first { it.identifier == "BOE-B-2024-1" }

        assertEquals(BoeSectionDto.IV, entry.section)
        assertEquals("ALMERIA", entry.title)
        assertEquals("JUZGADOS DE PRIMERA INSTANCIA E INSTRUCCIÓN", entry.issuingBody)
        assertNull(entry.control)
        assertNull(entry.heading)
        assertEquals("https://www.boe.es/diario_boe/txt.php?id=BOE-B-2024-1", entry.officialUrl)
        assertEquals(
            "https://www.boe.es/boe/dias/2024/01/02/pdfs/BOE-B-2024-1.pdf",
            entry.pdfUrl,
        )
    }

    @Test
    fun `la union de las secciones de ambos fixtures cubre las ocho del BOE`() {
        val secciones = listOf(
            parse("sumario-20261009.json", LocalDate.of(2026, 10, 9)),
            parse("sumario-20240102.json", LocalDate.of(2024, 1, 2)),
        ).flatMap { entries -> entries.map { it.section } }.toSet()

        assertEquals(
            BoeSectionDto.entries.toSet(),
            secciones,
            "Ningún fixture trae las 8 secciones por separado; su unión debe cubrirlas.",
        )
    }

    private fun parse(fixture: String, date: LocalDate): List<SummaryEntry> {
        val text = checkNotNull(javaClass.getResourceAsStream("/boe/$fixture")) {
            "No se encontró el fixture /boe/$fixture"
        }.readBytes().decodeToString()
        val response = DatoPublicoJson.decodeFromString<BoeSummaryResponseDto>(text)
        return response.toSummaryEntries(date)
    }
}
