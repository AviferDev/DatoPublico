package es.aviferdev.datopublico.model

import es.aviferdev.datopublico.serialization.DatoPublicoJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SerializationTest {

    private val resumen = Resumen(
        queCambia = "Se convocan 120 plazas de auxiliar administrativo.",
        aQuienAfecta = "Personas con título de ESO o equivalente.",
        cifrasClave = listOf("120 plazas", "20 días hábiles"),
        fuenteOficial = "https://www.boe.es/diario_boe/txt.php?id=BOE-B-2026-1234",
        plazo = Plazo(fechaLimite = "2026-10-30", descripcion = "20 días hábiles"),
    )

    private val publicacion = Publicacion(
        id = "BOE-B-2026-1234",
        titulo = "Resolución de convocatoria de plazas",
        fechaPublicacion = "2026-10-09",
        organismo = "Ministerio de Hacienda",
        seccion = SeccionBoe.II_B,
        epigrafe = "II.B. Autoridades y personal - Oposiciones y concursos",
        categoria = Categoria.OPOSICIONES_Y_EMPLEO_PUBLICO,
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=BOE-B-2026-1234",
        resumen = resumen,
        plazo = Plazo(fechaLimite = "2026-10-30", descripcion = "20 días hábiles"),
    )

    @Test
    fun publicationWithResumenRoundTrips() {
        val json = DatoPublicoJson.encodeToString(Publicacion.serializer(), publicacion)
        val decoded = DatoPublicoJson.decodeFromString(Publicacion.serializer(), json)

        assertEquals(publicacion, decoded)
        assertTrue(json.contains("\"oposiciones_y_empleo_publico\""))
        assertTrue(json.contains("\"II.B\""))
    }

    @Test
    fun publicationWithoutOptionalsRoundTripsAsNull() {
        val minima = Publicacion(
            id = "BOE-A-2026-1",
            titulo = "Ley de prueba",
            fechaPublicacion = "2026-10-09",
            organismo = null,
            seccion = SeccionBoe.I,
            epigrafe = null,
            categoria = Categoria.NORMAS_Y_LEGISLACION,
            urlOficial = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1",
        )

        val decoded = DatoPublicoJson.decodeFromString(
            Publicacion.serializer(),
            DatoPublicoJson.encodeToString(Publicacion.serializer(), minima),
        )

        assertEquals(minima, decoded)
        assertNull(decoded.epigrafe)
        assertNull(decoded.resumen)
        assertNull(decoded.plazo)
    }

    @Test
    fun plazoKeepsIsoDateAsString() {
        val plazo = Plazo(fechaLimite = "2026-10-30")
        val decoded = DatoPublicoJson.decodeFromString(
            Plazo.serializer(),
            DatoPublicoJson.encodeToString(Plazo.serializer(), plazo),
        )

        assertEquals(plazo, decoded)
        assertEquals("2026-10-30", decoded.fechaLimite)
        assertNull(decoded.descripcion)
    }

    @Test
    fun resumenDefaultsRoundTrip() {
        val minimo = Resumen(
            queCambia = "Cambio",
            aQuienAfecta = "Afectados",
            fuenteOficial = "https://www.boe.es/",
        )

        val decoded = DatoPublicoJson.decodeFromString(
            Resumen.serializer(),
            DatoPublicoJson.encodeToString(Resumen.serializer(), minimo),
        )

        assertEquals(minimo, decoded)
        assertEquals(emptyList(), decoded.cifrasClave)
        assertTrue(decoded.avisoIA)
        assertNull(decoded.plazo)
    }

    @Test
    fun dailySummaryRoundTrips() {
        val summary = DailySummary(fecha = "2026-10-09", publicaciones = listOf(publicacion))

        val decoded = DatoPublicoJson.decodeFromString(
            DailySummary.serializer(),
            DatoPublicoJson.encodeToString(DailySummary.serializer(), summary),
        )

        assertEquals(summary, decoded)
    }

    @Test
    fun dailySummaryIgnoresUnknownKeys() {
        val json = """
            {
              "fecha": "2026-10-09",
              "publicaciones": [],
              "campoDesconocido": "se ignora"
            }
        """.trimIndent()

        val decoded = DatoPublicoJson.decodeFromString(DailySummary.serializer(), json)

        assertEquals(DailySummary(fecha = "2026-10-09"), decoded)
    }

    @Test
    fun dailySummaryWithoutPublicacionesDefaults() {
        val decoded = DatoPublicoJson.decodeFromString(
            DailySummary.serializer(),
            """{"fecha":"2026-10-09"}""",
        )

        assertEquals(emptyList(), decoded.publicaciones)
    }
}
