package es.aviferdev.datopublico.backend.ingesta.sumario

import es.aviferdev.datopublico.model.SeccionBoeDto
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Parseo de los fixtures **reales** del sumario del BOE (sin red).
 *
 * `20261009` incluye la Sección V.A (excluida) y `item` en ambas formas;
 * `20240102` incluye la Sección IV y `departamento` como objeto. Ambos deben
 * parsearse y filtrarse a las secciones del corpus.
 */
class SumarioParsingTest {

    @Test
    fun `fixture real 20261009 devuelve solo las secciones del corpus`() {
        val entradas = parse("sumario-20261009.json", LocalDate.of(2026, 10, 9))

        assertEquals(
            setOf(SeccionBoeDto.I, SeccionBoeDto.II_A, SeccionBoeDto.II_B, SeccionBoeDto.III, SeccionBoeDto.V_B),
            entradas.map { it.seccion }.toSet(),
        )
        assertEquals(205, entradas.size, "1:5 + 2A:16 + 2B:31 + 3:69 + 5B:84")
        // La Sección V.A del fixture queda fuera (32 entradas en la fuente).
        assertFalse(entradas.any { it.identificador == "BOE-B-2026-32742" })
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
        assertTrue(entrada.titulo.startsWith("Acuerdo internacional administrativo"))
    }

    @Test
    fun `fixture real 20240102 con item y departamento en forma de objeto se parsea`() {
        val entradas = parse("sumario-20240102.json", LocalDate.of(2024, 1, 2))

        assertEquals(
            setOf(SeccionBoeDto.II_A, SeccionBoeDto.II_B, SeccionBoeDto.III, SeccionBoeDto.V_B),
            entradas.map { it.seccion }.toSet(),
        )
        assertEquals(64, entradas.size, "2A:6 + 2B:24 + 3:18 + 5B:16")
        // Excluidas: Sección IV (24), V.A (51) y V.C (4).
        assertFalse(entradas.any { it.identificador == "BOE-B-2024-25" })
        assertTrue(entradas.any { it.identificador == "BOE-B-2024-76" })
    }

    private fun parse(fixture: String, fecha: LocalDate): List<EntradaSumario> {
        val texto = checkNotNull(javaClass.getResourceAsStream("/boe/$fixture")) {
            "No se encontró el fixture /boe/$fixture"
        }.readBytes().decodeToString()
        val response = DatoPublicoJson.decodeFromString<BoeSumarioResponseDto>(texto)
        return response.toEntradasSumario(fecha)
    }
}
