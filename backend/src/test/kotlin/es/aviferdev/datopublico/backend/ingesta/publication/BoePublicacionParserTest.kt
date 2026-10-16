package es.aviferdev.datopublico.backend.ingesta.publication

import es.aviferdev.datopublico.backend.ingestion.summary.BoeSummaryResponseDto
import es.aviferdev.datopublico.backend.ingestion.summary.SummaryEntry
import es.aviferdev.datopublico.backend.ingestion.summary.toSummaryEntries
import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.BoeSectionDto
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Orquestador [BoePublicacionParser] sobre los **fixtures reales** del sumario y
 * del XML (sin red): cubre las secciones, el rango y, desde FT00012, la
 * **categoría** y el **plazo** de cada publicación.
 *
 * El `MockEngine` sirve el XML local correspondiente al `id` pedido, de modo que
 * el flujo sumario real → descarga → parseo → [Publicacion] se ejercita de punta
 * a punta con datos reales de las ocho secciones.
 */
class BoePublicacionParserTest {

    @Test
    fun `fixture real de cada section produce una publication completa`() = runTest {
        val entries = entradasDelSumario()
        val parser = BoePublicacionHttpParser(BoeTextoHttpClient(clientQueSirveFixtures()))

        RANGOS_POR_ID.forEach { (identifier, rango) ->
            val entry = assertNotNull(entries[identifier], "sin entrada de sumario para $identifier")
            val publication = parser.parsear(entry)

            assertEquals(identifier, publication.id)
            assertTrue(publication.title.isNotBlank(), "$identifier sin título")
            assertEquals(entry.publicationDate, publication.publicationDate)
            assertEquals(entry.section, publication.section)
            assertEquals(entry.officialUrl, publication.officialUrl)
            assertEquals(rango, publication.rango, "$identifier: rango")
            assertEquals(CATEGORIAS_POR_ID[identifier], publication.category, "$identifier: categoría")
            assertEquals(PLAZOS_POR_ID[identifier], publication.deadline?.deadlineDate, "$identifier: plazo")
            val texto = assertNotNull(publication.texto, "$identifier sin texto")
            assertTrue(texto.isNotBlank(), "$identifier con texto vacío")
        }
    }

    @Test
    fun `cubre las ocho secciones del BOE`() = runTest {
        val entries = entradasDelSumario()
        val parser = BoePublicacionHttpParser(BoeTextoHttpClient(clientQueSirveFixtures()))

        val secciones = FIXTURES_POR_ID.keys
            .map { identifier -> assertNotNull(entries[identifier], "sin entrada $identifier") }
            .map { parser.parsear(it).section }
            .toSet()

        assertEquals(BoeSectionDto.entries.toSet(), secciones)
    }

    @Test
    fun `una entry sin urlXml no hace ninguna peticion y deja texto null`() = runTest {
        var peticiones = 0
        val client = clientCon(MockEngine { peticiones += 1; respond("", HttpStatusCode.OK) })
        val parser = BoePublicacionHttpParser(BoeTextoHttpClient(client))

        val publication = parser.parsear(entry(urlXml = null))

        assertEquals(0, peticiones, "No debe pedirse texto sin urlXml")
        assertNull(publication.texto)
        assertEquals("BOE-A-1", publication.id)
        assertTrue(publication.officialUrl.isNotBlank())
    }

    @Test
    fun `un 404 produce una publication valida con texto null`() = runTest {
        val client = clientCon(MockEngine { respondError(HttpStatusCode.NotFound, "sin texto") })
        val parser = BoePublicacionHttpParser(BoeTextoHttpClient(client))

        val publication = parser.parsear(entry())

        assertNull(publication.texto)
        assertNull(publication.rango)
        assertEquals("BOE-A-1", publication.id)
        assertEquals(BoeSectionDto.I, publication.section)
    }

    private fun entradasDelSumario(): Map<String, SummaryEntry> =
        listOf(
            "sumario-20261009.json" to LocalDate.of(2026, 10, 9),
            "sumario-20240102.json" to LocalDate.of(2024, 1, 2),
        )
            .flatMap { (name, date) -> parseSumario(name, date) }
            .associateBy { it.identifier }

    private fun parseSumario(name: String, date: LocalDate): List<SummaryEntry> =
        DatoPublicoJson.decodeFromString<BoeSummaryResponseDto>(recurso(name)).toSummaryEntries(date)

    /** Sirve el fixture XML local cuyo `id` coincide con el `urlXml` pedido. */
    private fun clientQueSirveFixtures(): HttpClient = clientCon(
        MockEngine { request ->
            val ruta = FIXTURES_POR_ID[request.url.parameters["id"]]
            if (ruta != null) {
                respond(
                    content = recurso(ruta),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/xml"),
                )
            } else {
                respondError(HttpStatusCode.NotFound, "sin fixture para el id pedido")
            }
        },
    )

    private fun clientCon(engine: MockEngine): HttpClient = HttpClient(engine) { expectSuccess = false }

    private fun entry(
        urlXml: String? = URL_XML,
        issuingBody: String? = "MINISTERIO DEL SUMARIO",
        urlPdf: String? = null,
    ): SummaryEntry = SummaryEntry(
        identifier = "BOE-A-1",
        control = "2026/1",
        title = "Título de prueba",
        publicationDate = "2026-10-09",
        section = BoeSectionDto.I,
        issuingBody = issuingBody,
        heading = "Epígrafe",
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-1",
        urlXml = urlXml,
        urlPdf = urlPdf,
    )

    /** Lee un recurso de `src/test/resources/boe` como texto UTF-8. */
    private fun recurso(ruta: String): String =
        checkNotNull(javaClass.getResourceAsStream("/boe/$ruta")) { "No se encontró /boe/$ruta" }
            .readBytes()
            .decodeToString()

    private companion object {
        const val URL_XML = "https://www.boe.es/diario_boe/xml.php?id=BOE-A-1"

        /** Identificador del BOE → fixture XML real en `src/test/resources/boe`. */
        val FIXTURES_POR_ID: Map<String, String> = mapOf(
            "BOE-A-2026-20979" to "texto-I-2026-20979.xml",
            "BOE-A-2024-87" to "texto-IIA-2024-87.xml",
            "BOE-A-2024-93" to "texto-IIB-2024-93.xml",
            "BOE-A-2024-117" to "texto-III-2024-117.xml",
            "BOE-B-2024-1" to "texto-IV-2024-1.xml",
            "BOE-B-2024-25" to "texto-VA-2024-25.xml",
            "BOE-B-2024-76" to "texto-VB-2024-76.xml",
            "BOE-B-2024-92" to "texto-VC-2024-92.xml",
        )

        /** Rango normativo esperado por identificador (`null` si la sección no lo trae). */
        val RANGOS_POR_ID: Map<String, String?> = mapOf(
            "BOE-A-2026-20979" to "Real Decreto",
            "BOE-A-2024-87" to "Resolución",
            "BOE-A-2024-93" to "Resolución",
            "BOE-A-2024-117" to "Orden",
            "BOE-B-2024-1" to null,
            "BOE-B-2024-25" to null,
            "BOE-B-2024-76" to null,
            "BOE-B-2024-92" to null,
        )

        /** Categoría curada esperada por identificador (FT00012, escenario 1). */
        val CATEGORIAS_POR_ID: Map<String, CategoryDto> = mapOf(
            "BOE-A-2026-20979" to CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID,
            "BOE-A-2024-87" to CategoryDto.APPOINTMENTS_AND_DISMISSALS,
            "BOE-A-2024-93" to CategoryDto.PUBLIC_EMPLOYMENT_AND_JOB_OFFERS,
            "BOE-A-2024-117" to CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID,
            "BOE-B-2024-1" to CategoryDto.OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
            "BOE-B-2024-25" to CategoryDto.PUBLIC_INFORMATION_AND_CONCESSIONS,
            "BOE-B-2024-76" to CategoryDto.PUBLIC_INFORMATION_AND_CONCESSIONS,
            "BOE-B-2024-92" to CategoryDto.OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
        )

        /** Fecha límite esperada por identificador (FT00012, escenarios 2 y 3). */
        val PLAZOS_POR_ID: Map<String, String?> = mapOf(
            "BOE-A-2026-20979" to null,
            "BOE-A-2024-87" to null,
            "BOE-A-2024-93" to "2024-01-23",
            "BOE-A-2024-117" to "2024-01-23",
            "BOE-B-2024-1" to null,
            "BOE-B-2024-25" to null,
            "BOE-B-2024-76" to null,
            "BOE-B-2024-92" to null,
        )
    }
}
