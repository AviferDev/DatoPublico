package es.aviferdev.datopublico.backend.persistencia

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.PlazoDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests **sin base de datos** del mapeo dominio ↔ entidad de publicación (gate).
 *
 * Cubren el viaje de ida y vuelta con metadatos completos (categoría, sección,
 * epígrafe y plazo) y el caso con opcionales nulos (parser de FT00007 antes de
 * FT00012). No abren ninguna conexión.
 */
class PublicacionPersistenciaTest {

    @Test
    fun `mapea ida y vuelta una publicacion completa`() {
        val publicacion = publicacionCompleta()

        val recuperada = publicacion.aPublicacionEntity().aPublicacion()

        assertEquals(publicacion, recuperada)
    }

    @Test
    fun `guarda seccion y categoria por el nombre del enum`() {
        val entidad = publicacionCompleta().aPublicacionEntity()

        assertEquals("II_A", entidad.seccion)
        assertEquals("BECAS_SUBVENCIONES_Y_AYUDAS", entidad.categoria)
    }

    @Test
    fun `mapea el plazo en sus dos columnas`() {
        val entidad = publicacionCompleta().aPublicacionEntity()

        assertEquals("2026-11-30", entidad.plazoFechaLimite)
        assertEquals("Diez días hábiles", entidad.plazoDescripcion)
    }

    @Test
    fun `conserva los nulos del parser antes de FT00012`() {
        val publicacion = Publicacion(
            id = "BOE-A-2024-87",
            titulo = "Resolución de 21 de diciembre de 2023",
            fechaPublicacion = "2024-01-02",
            organismo = null,
            seccion = SeccionBoeDto.II_A,
            epigrafe = null,
            texto = null,
            urlOficial = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2024-87",
            urlXml = null,
            urlPdf = null,
            rango = null,
        )

        val entidad = publicacion.aPublicacionEntity()
        val recuperada = entidad.aPublicacion()

        assertNull(entidad.categoria)
        assertNull(entidad.plazoFechaLimite)
        assertNull(entidad.plazoDescripcion)
        assertNull(recuperada.organismo)
        assertNull(recuperada.epigrafe)
        assertNull(recuperada.texto)
        assertNull(recuperada.categoria)
        assertNull(recuperada.plazo)
        assertEquals(publicacion, recuperada)
    }

    @Test
    fun `un plazo sin descripcion se conserva`() {
        val publicacion = publicacionCompleta().copy(
            plazo = PlazoDto(fechaLimite = "2026-12-15"),
        )

        assertEquals(publicacion.plazo, publicacion.aPublicacionEntity().aPublicacion().plazo)
    }

    private fun publicacionCompleta(): Publicacion = Publicacion(
        id = "BOE-A-2026-20979",
        titulo = "Resolución de 8 de octubre de 2026",
        fechaPublicacion = "2026-10-09",
        organismo = "MINISTERIO DE EDUCACIÓN",
        seccion = SeccionBoeDto.II_A,
        epigrafe = "Becas y subvenciones",
        texto = "Primero. Convocar...\nSegundo. El plazo...",
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-20979",
        urlXml = "https://www.boe.es/diario_boe/xml.php?id=BOE-A-2026-20979",
        urlPdf = "https://www.boe.es/boe/dias/2026/10/09/pdfs/A00001-00002.pdf",
        rango = "Resolución",
        categoria = CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS,
        plazo = PlazoDto(fechaLimite = "2026-11-30", descripcion = "Diez días hábiles"),
    )
}
