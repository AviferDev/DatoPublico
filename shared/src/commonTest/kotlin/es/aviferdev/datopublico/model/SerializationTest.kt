package es.aviferdev.datopublico.model

import es.aviferdev.datopublico.serialization.DatoPublicoJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SerializationTest {

    private val summary = SummaryDto(
        whatChanges = "Se convocan 120 plazas de auxiliar administrativo.",
        whoIsAffected = "Personas con título de ESO o equivalente.",
        keyFigures = listOf("120 plazas", "20 días hábiles"),
        officialSource = "https://www.boe.es/diario_boe/txt.php?id=BOE-B-2026-1234",
        deadline = DeadlineDto(deadlineDate = "2026-10-30", description = "20 días hábiles"),
    )

    private val publication = PublicationDto(
        id = "BOE-B-2026-1234",
        title = "Resolución de convocatoria de plazas",
        publicationDate = "2026-10-09",
        issuingBody = "Ministerio de Hacienda",
        section = BoeSectionDto.II_B,
        heading = "II.B. Autoridades y personal - Oposiciones y concursos",
        category = CategoryDto.PUBLIC_EMPLOYMENT_AND_JOB_OFFERS,
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-B-2026-1234",
        summary = summary,
        deadline = DeadlineDto(deadlineDate = "2026-10-30", description = "20 días hábiles"),
    )

    @Test
    fun publicationWithSummaryRoundTrips() {
        val json = DatoPublicoJson.encodeToString(PublicationDto.serializer(), publication)
        val decoded = DatoPublicoJson.decodeFromString(PublicationDto.serializer(), json)

        assertEquals(publication, decoded)
        assertTrue(json.contains("\"oposiciones_y_empleo_publico\""))
        assertTrue(json.contains("\"II.B\""))
    }

    @Test
    fun publicationWithoutOptionalsRoundTripsAsNull() {
        val minima = PublicationDto(
            id = "BOE-A-2026-1",
            title = "Ley de prueba",
            publicationDate = "2026-10-09",
            issuingBody = null,
            section = BoeSectionDto.I,
            heading = null,
            category = CategoryDto.NORMS_AND_LEGISLATION,
            officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-1",
        )

        val decoded = DatoPublicoJson.decodeFromString(
            PublicationDto.serializer(),
            DatoPublicoJson.encodeToString(PublicationDto.serializer(), minima),
        )

        assertEquals(minima, decoded)
        assertNull(decoded.heading)
        assertNull(decoded.summary)
        assertNull(decoded.deadline)
    }

    @Test
    fun deadlineKeepsIsoDateAsString() {
        val deadline = DeadlineDto(deadlineDate = "2026-10-30")
        val decoded = DatoPublicoJson.decodeFromString(
            DeadlineDto.serializer(),
            DatoPublicoJson.encodeToString(DeadlineDto.serializer(), deadline),
        )

        assertEquals(deadline, decoded)
        assertEquals("2026-10-30", decoded.deadlineDate)
        assertNull(decoded.description)
    }

    @Test
    fun summaryDefaultsRoundTrip() {
        val minimo = SummaryDto(
            whatChanges = "Cambio",
            whoIsAffected = "Afectados",
            officialSource = "https://www.boe.es/",
        )

        val decoded = DatoPublicoJson.decodeFromString(
            SummaryDto.serializer(),
            DatoPublicoJson.encodeToString(SummaryDto.serializer(), minimo),
        )

        assertEquals(minimo, decoded)
        assertEquals(emptyList(), decoded.keyFigures)
        assertTrue(decoded.aiNotice)
        assertNull(decoded.deadline)
    }

    @Test
    fun dailySummaryRoundTrips() {
        val summary = DailySummaryDto(date = "2026-10-09", publications = listOf(publication))

        val decoded = DatoPublicoJson.decodeFromString(
            DailySummaryDto.serializer(),
            DatoPublicoJson.encodeToString(DailySummaryDto.serializer(), summary),
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

        val decoded = DatoPublicoJson.decodeFromString(DailySummaryDto.serializer(), json)

        assertEquals(DailySummaryDto(date = "2026-10-09"), decoded)
    }

    @Test
    fun dailySummaryWithoutPublicationsDefaults() {
        val decoded = DatoPublicoJson.decodeFromString(
            DailySummaryDto.serializer(),
            """{"fecha":"2026-10-09"}""",
        )

        assertEquals(emptyList(), decoded.publications)
    }
}
