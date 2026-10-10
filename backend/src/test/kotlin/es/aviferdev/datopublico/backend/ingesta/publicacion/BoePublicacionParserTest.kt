package es.aviferdev.datopublico.backend.ingesta.publicacion

import es.aviferdev.datopublico.backend.ingesta.sumario.BoeSumarioResponseDto
import es.aviferdev.datopublico.backend.ingesta.sumario.EntradaSumario
import es.aviferdev.datopublico.backend.ingesta.sumario.toEntradasSumario
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.SeccionBoeDto
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
    fun `fixture real de cada seccion produce una publicacion completa`() = runTest {
        val entradas = entradasDelSumario()
        val parser = BoePublicacionHttpParser(BoeTextoHttpClient(clientQueSirveFixtures()))

        RANGOS_POR_ID.forEach { (identificador, rango) ->
            val entrada = assertNotNull(entradas[identificador], "sin entrada de sumario para $identificador")
            val publicacion = parser.parsear(entrada)

            assertEquals(identificador, publicacion.id)
            assertTrue(publicacion.titulo.isNotBlank(), "$identificador sin título")
            assertEquals(entrada.fechaPublicacion, publicacion.fechaPublicacion)
            assertEquals(entrada.seccion, publicacion.seccion)
            assertEquals(entrada.urlOficial, publicacion.urlOficial)
            assertEquals(rango, publicacion.rango, "$identificador: rango")
            assertEquals(CATEGORIAS_POR_ID[identificador], publicacion.categoria, "$identificador: categoría")
            assertEquals(PLAZOS_POR_ID[identificador], publicacion.plazo?.fechaLimite, "$identificador: plazo")
            val texto = assertNotNull(publicacion.texto, "$identificador sin texto")
            assertTrue(texto.isNotBlank(), "$identificador con texto vacío")
        }
    }

    @Test
    fun `cubre las ocho secciones del BOE`() = runTest {
        val entradas = entradasDelSumario()
        val parser = BoePublicacionHttpParser(BoeTextoHttpClient(clientQueSirveFixtures()))

        val secciones = FIXTURES_POR_ID.keys
            .map { identificador -> assertNotNull(entradas[identificador], "sin entrada $identificador") }
            .map { parser.parsear(it).seccion }
            .toSet()

        assertEquals(SeccionBoeDto.entries.toSet(), secciones)
    }

    @Test
    fun `una entrada sin urlXml no hace ninguna peticion y deja texto null`() = runTest {
        var peticiones = 0
        val client = clientCon(MockEngine { peticiones += 1; respond("", HttpStatusCode.OK) })
        val parser = BoePublicacionHttpParser(BoeTextoHttpClient(client))

        val publicacion = parser.parsear(entrada(urlXml = null))

        assertEquals(0, peticiones, "No debe pedirse texto sin urlXml")
        assertNull(publicacion.texto)
        assertEquals("BOE-A-1", publicacion.id)
        assertTrue(publicacion.urlOficial.isNotBlank())
    }

    @Test
    fun `un 404 produce una publicacion valida con texto null`() = runTest {
        val client = clientCon(MockEngine { respondError(HttpStatusCode.NotFound, "sin texto") })
        val parser = BoePublicacionHttpParser(BoeTextoHttpClient(client))

        val publicacion = parser.parsear(entrada())

        assertNull(publicacion.texto)
        assertNull(publicacion.rango)
        assertEquals("BOE-A-1", publicacion.id)
        assertEquals(SeccionBoeDto.I, publicacion.seccion)
    }

    private fun entradasDelSumario(): Map<String, EntradaSumario> =
        listOf(
            "sumario-20261009.json" to LocalDate.of(2026, 10, 9),
            "sumario-20240102.json" to LocalDate.of(2024, 1, 2),
        )
            .flatMap { (nombre, fecha) -> parseSumario(nombre, fecha) }
            .associateBy { it.identificador }

    private fun parseSumario(nombre: String, fecha: LocalDate): List<EntradaSumario> =
        DatoPublicoJson.decodeFromString<BoeSumarioResponseDto>(recurso(nombre)).toEntradasSumario(fecha)

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

    private fun entrada(
        urlXml: String? = URL_XML,
        organismo: String? = "MINISTERIO DEL SUMARIO",
        urlPdf: String? = null,
    ): EntradaSumario = EntradaSumario(
        identificador = "BOE-A-1",
        control = "2026/1",
        titulo = "Título de prueba",
        fechaPublicacion = "2026-10-09",
        seccion = SeccionBoeDto.I,
        organismo = organismo,
        epigrafe = "Epígrafe",
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-1",
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
        val CATEGORIAS_POR_ID: Map<String, CategoriaDto> = mapOf(
            "BOE-A-2026-20979" to CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS,
            "BOE-A-2024-87" to CategoriaDto.NOMBRAMIENTOS_Y_CESES,
            "BOE-A-2024-93" to CategoriaDto.OPOSICIONES_Y_EMPLEO_PUBLICO,
            "BOE-A-2024-117" to CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS,
            "BOE-B-2024-1" to CategoriaDto.OTRAS_DISPOSICIONES_Y_ANUNCIOS,
            "BOE-B-2024-25" to CategoriaDto.INFORMACION_PUBLICA_Y_CONCESIONES,
            "BOE-B-2024-76" to CategoriaDto.INFORMACION_PUBLICA_Y_CONCESIONES,
            "BOE-B-2024-92" to CategoriaDto.OTRAS_DISPOSICIONES_Y_ANUNCIOS,
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
