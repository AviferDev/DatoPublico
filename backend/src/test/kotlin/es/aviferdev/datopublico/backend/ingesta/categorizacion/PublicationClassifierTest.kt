package es.aviferdev.datopublico.backend.ingesta.categorizacion

import es.aviferdev.datopublico.backend.ingesta.sumario.BoeSumarioResponseDto
import es.aviferdev.datopublico.backend.ingesta.sumario.EntradaSumario
import es.aviferdev.datopublico.backend.ingesta.sumario.toEntradasSumario
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import es.aviferdev.datopublico.serialization.DatoPublicoJson
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Clasificador curado de publicaciones (FT00012), sin red ni base de datos.
 *
 * La muestra real de las ocho secciones se toma de los **fixtures del sumario ya
 * versionados** (los mismos que ejercita `BoePublicacionParserTest`): no se
 * inventan datos. Escenarios 1, 3 y 4 del spec.
 */
class PublicationClassifierTest {

    private val classifier = PublicationClassifier()

    @Test
    fun `clasifica la muestra real de las ocho secciones por su categoria esperada`() {
        val entradas = entradasDelSumario()

        EXPECTED_CATEGORIES.forEach { (identificador, category) ->
            val entrada = assertNotNull(entradas[identificador], "sin entrada de sumario para $identificador")
            val actual = classifier.classify(entrada.seccion, entrada.epigrafe, entrada.titulo)
            assertEquals(category, actual, "$identificador (sección ${entrada.seccion})")
        }
    }

    @Test
    fun `conserva el epigrafe real del sumario sin mutarlo`() {
        val entradas = entradasDelSumario()

        EXPECTED_HEADINGS.forEach { (identificador, heading) ->
            val entrada = assertNotNull(entradas[identificador], "sin entrada de sumario para $identificador")
            assertEquals(heading, entrada.epigrafe, "$identificador: epígrafe conservado")
        }
    }

    @Test
    fun `las entradas reales sin epigrafe caen en la categoria por defecto de su seccion`() {
        val entradas = entradasDelSumario()

        listOf("BOE-B-2024-1", "BOE-B-2024-92").forEach { identificador ->
            val entrada = assertNotNull(entradas[identificador], "sin entrada de sumario para $identificador")
            assertNull(entrada.epigrafe, "$identificador: sin epígrafe")
            assertEquals(
                CategoriaDto.OTRAS_DISPOSICIONES_Y_ANUNCIOS,
                classifier.classify(entrada.seccion, entrada.epigrafe, entrada.titulo),
                "$identificador",
            )
        }
    }

    @Test
    fun `los casos ambiguos sin palabras clave usan la categoria por defecto de la seccion`() {
        SeccionBoeDto.entries.forEach { section ->
            val actual = classifier.classify(section, null, "Anuncio sin palabras clave reconocibles")
            assertEquals(DEFAULTS_POR_SECCION[section], actual, "sección $section")
        }
    }

    @Test
    fun `ante varias reglas aplicables gana la primera del orden documentado`() {
        // «Ayudas» (BECAS) precede en el orden a «premios» (PREMIOS).
        val actual = classifier.classify(SeccionBoeDto.I, "Ayudas y premios", "Título de la publicación")

        assertEquals(CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS, actual)
    }

    @Test
    fun `es determinista para la misma entrada`() {
        val entrada = assertNotNull(entradasDelSumario()["BOE-A-2024-93"], "sin entrada de la convocatoria")

        val primera = classifier.classify(entrada.seccion, entrada.epigrafe, entrada.titulo)
        val segunda = classifier.classify(entrada.seccion, entrada.epigrafe, entrada.titulo)

        assertEquals(primera, segunda)
    }

    private fun entradasDelSumario(): Map<String, EntradaSumario> =
        listOf(
            "sumario-20261009.json" to LocalDate.of(2026, 10, 9),
            "sumario-20240102.json" to LocalDate.of(2024, 1, 2),
        )
            .flatMap { (nombre, fecha) ->
                DatoPublicoJson.decodeFromString<BoeSumarioResponseDto>(recurso(nombre)).toEntradasSumario(fecha)
            }
            .associateBy { it.identificador }

    /** Lee un recurso de `src/test/resources/boe` como texto UTF-8. */
    private fun recurso(ruta: String): String =
        checkNotNull(javaClass.getResourceAsStream("/boe/$ruta")) { "No se encontró /boe/$ruta" }
            .readBytes()
            .decodeToString()

    private companion object {
        /** Identificador real → categoría curada esperada (escenario 1 del spec). */
        val EXPECTED_CATEGORIES: Map<String, CategoriaDto> = mapOf(
            "BOE-A-2026-20979" to CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS,
            "BOE-A-2024-87" to CategoriaDto.NOMBRAMIENTOS_Y_CESES,
            "BOE-A-2024-93" to CategoriaDto.OPOSICIONES_Y_EMPLEO_PUBLICO,
            "BOE-A-2024-117" to CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS,
            "BOE-B-2024-1" to CategoriaDto.OTRAS_DISPOSICIONES_Y_ANUNCIOS,
            "BOE-B-2024-25" to CategoriaDto.INFORMACION_PUBLICA_Y_CONCESIONES,
            "BOE-B-2024-76" to CategoriaDto.INFORMACION_PUBLICA_Y_CONCESIONES,
            "BOE-B-2024-92" to CategoriaDto.OTRAS_DISPOSICIONES_Y_ANUNCIOS,
        )

        /** Identificador real → epígrafe del sumario (puede ser `null`). */
        val EXPECTED_HEADINGS: Map<String, String?> = mapOf(
            "BOE-A-2026-20979" to "Subvenciones",
            "BOE-A-2024-87" to "Destinos",
            "BOE-A-2024-93" to "Personal laboral",
            "BOE-A-2024-117" to "Ayudas",
            "BOE-B-2024-1" to null,
            "BOE-B-2024-25" to null,
            "BOE-B-2024-76" to null,
            "BOE-B-2024-92" to null,
        )

        /** Categoría por defecto por sección (misma tabla que el clasificador). */
        val DEFAULTS_POR_SECCION: Map<SeccionBoeDto, CategoriaDto> = mapOf(
            SeccionBoeDto.I to CategoriaDto.NORMAS_Y_LEGISLACION,
            SeccionBoeDto.II_A to CategoriaDto.NOMBRAMIENTOS_Y_CESES,
            SeccionBoeDto.II_B to CategoriaDto.OPOSICIONES_Y_EMPLEO_PUBLICO,
            SeccionBoeDto.III to CategoriaDto.OTRAS_DISPOSICIONES_Y_ANUNCIOS,
            SeccionBoeDto.IV to CategoriaDto.OTRAS_DISPOSICIONES_Y_ANUNCIOS,
            SeccionBoeDto.V_A to CategoriaDto.INFORMACION_PUBLICA_Y_CONCESIONES,
            SeccionBoeDto.V_B to CategoriaDto.OTRAS_DISPOSICIONES_Y_ANUNCIOS,
            SeccionBoeDto.V_C to CategoriaDto.OTRAS_DISPOSICIONES_Y_ANUNCIOS,
        )
    }
}
