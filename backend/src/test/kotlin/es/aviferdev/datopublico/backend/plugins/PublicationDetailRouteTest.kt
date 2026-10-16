package es.aviferdev.datopublico.backend.plugins

import es.aviferdev.datopublico.backend.ingesta.publication.Publicacion
import es.aviferdev.datopublico.backend.persistence.PublicationRepository
import es.aviferdev.datopublico.backend.persistence.SummaryEntity
import es.aviferdev.datopublico.backend.persistence.SummaryRepository
import es.aviferdev.datopublico.backend.publication.PublicationDetailService
import es.aviferdev.datopublico.backend.rag.review.SummaryState
import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.DeadlineDto
import es.aviferdev.datopublico.model.PublicationDto
import es.aviferdev.datopublico.model.BoeSectionDto
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests de gate de la ruta `GET /v1/publicacion/{id}` (FT00027) con `testApplication`
 * y repositorios en memoria: `200` con el JSON `PublicationDto` (resumen,
 * categoría, `urlOficial` y plazo), `400` por id en blanco/desmesurado, `404` por
 * id desconocido o resumen no publicado y `404` cuando el servicio no se registra
 * (sin base de datos).
 */
class PublicationDetailRouteTest {

    @Test
    fun `detail responds 200 with the publication json`() = testApplication {
        installDetail(
            publications = listOf(publication(PUBLISHED_ID)),
            summaries = listOf(summary(PUBLISHED_ID, SummaryState.PUBLISHED.persistedValue)),
        )

        val response = client.get("/v1/publicacion/$PUBLISHED_ID")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val detail = DatoPublicoJson.decodeFromString<PublicationDto>(response.bodyAsText())
        assertEquals(PUBLISHED_ID, detail.id)
        assertEquals(CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID, detail.category)
        assertEquals("https://www.boe.es/diario_boe/txt.php?id=$PUBLISHED_ID", detail.officialUrl)
        assertEquals(DeadlineDto("2026-10-30", "20 días hábiles"), detail.deadline)
        val summary = detail.summary
        assertNotNull(summary)
        assertEquals("Cambia algo en $PUBLISHED_ID", summary.whatChanges)
        assertEquals("https://www.boe.es/diario_boe/txt.php?id=$PUBLISHED_ID", summary.officialSource)
        assertTrue(summary.aiNotice)
    }

    @Test
    fun `detail responds 400 when the id is blank or oversized`() = testApplication {
        installDetail(
            publications = listOf(publication(PUBLISHED_ID)),
            summaries = listOf(summary(PUBLISHED_ID, SummaryState.PUBLISHED.persistedValue)),
        )

        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/publicacion/%20").status)
        assertEquals(
            HttpStatusCode.BadRequest,
            client.get("/v1/publicacion/${"a".repeat(PublicationDetailService.MAX_ID_LENGTH + 1)}").status,
        )
    }

    @Test
    fun `detail responds 404 for an unknown id or a non published summary`() = testApplication {
        installDetail(
            publications = listOf(publication(PUBLISHED_ID), publication(QUEUED_ID)),
            summaries = listOf(summary(QUEUED_ID, SummaryState.IN_REVIEW.persistedValue)),
        )

        assertEquals(HttpStatusCode.NotFound, client.get("/v1/publicacion/BOE-A-DESCONOCIDA").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/publicacion/$QUEUED_ID").status)
    }

    @Test
    fun `detail responds 404 when the service is not registered`() = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }

        assertEquals(HttpStatusCode.NotFound, client.get("/v1/publicacion/$PUBLISHED_ID").status)
    }

    /** Registra serialización + rutas con el servicio del detalle sobre dobles en memoria. */
    private fun ApplicationTestBuilder.installDetail(
        publications: List<Publicacion>,
        summaries: List<SummaryEntity>,
    ) {
        application {
            configureSerialization()
            configureRouting(
                detailService = PublicationDetailService(
                    InMemoryPublicationRepository(publications),
                    InMemorySummaryRepository(summaries),
                ),
            )
        }
    }

    private fun publication(id: String): Publicacion = Publicacion(
        id = id,
        title = "Convocatoria de prueba",
        publicationDate = DATE,
        issuingBody = "Ministerio de prueba",
        section = BoeSectionDto.II_B,
        heading = "Becas y ayudas",
        texto = null,
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=$id",
        urlXml = null,
        urlPdf = null,
        rango = "Resolución",
        category = CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID,
        deadline = DeadlineDto("2026-10-30", "20 días hábiles"),
    )

    private fun summary(publicationId: String, status: String): SummaryEntity = SummaryEntity(
        publicationId = publicationId,
        whatChanges = "Cambia algo en $publicationId",
        whomItAffects = "A la ciudadanía",
        officialSource = "https://www.boe.es/diario_boe/txt.php?id=$publicationId",
        status = status,
    )

    /** Doble en memoria de [PublicationRepository] para el gate. */
    private class InMemoryPublicationRepository(
        private val publications: List<Publicacion>,
    ) : PublicationRepository {
        override fun save(publication: Publicacion): Publicacion = publication

        override fun findById(id: String): Publicacion? =
            publications.firstOrNull { publication -> publication.id == id }

        override fun listByDate(publicationDate: String): List<Publicacion> =
            publications.filter { publication -> publication.publicationDate == publicationDate }
    }

    /** Doble en memoria de [SummaryRepository] para el gate. */
    private class InMemorySummaryRepository(
        private val summaries: List<SummaryEntity>,
    ) : SummaryRepository {
        override fun save(summary: SummaryEntity): SummaryEntity = summary

        override fun findByPublication(publicationId: String): SummaryEntity? =
            summaries.firstOrNull { summary -> summary.publicationId == publicationId }
    }

    private companion object {
        const val DATE = "2026-10-11"
        const val PUBLISHED_ID = "BOE-A-2026-20979"
        const val QUEUED_ID = "BOE-A-2026-20980"
    }
}
