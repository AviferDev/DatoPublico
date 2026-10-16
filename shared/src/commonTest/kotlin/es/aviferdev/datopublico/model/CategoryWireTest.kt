package es.aviferdev.datopublico.model

import es.aviferdev.datopublico.serialization.DatoPublicoJson
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryWireTest {

    private val tokensEsperados = mapOf(
        CategoryDto.NORMS_AND_LEGISLATION to "normas_y_legislacion",
        CategoryDto.APPOINTMENTS_AND_DISMISSALS to "nombramientos_y_ceses",
        CategoryDto.PUBLIC_EMPLOYMENT_AND_JOB_OFFERS to "oposiciones_y_empleo_publico",
        CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID to "becas_subvenciones_y_ayudas",
        CategoryDto.AWARDS to "premios",
        CategoryDto.AGREEMENTS_AND_CONVENTIONS to "convenios_y_acuerdos",
        CategoryDto.EDUCATION_AND_STUDY_PLANS to "educacion_y_planes_de_estudio",
        CategoryDto.ENVIRONMENT to "medio_ambiente",
        CategoryDto.APPEALS_AND_RESOLUTIONS to "recursos_y_resoluciones",
        CategoryDto.PUBLIC_INFORMATION_AND_CONCESSIONS to "informacion_publica_y_concesiones",
        CategoryDto.OTHER_PROVISIONS_AND_ANNOUNCEMENTS to "otras_disposiciones_y_anuncios",
    )

    private val tokensSeccion = mapOf(
        BoeSectionDto.I to "I",
        BoeSectionDto.II_A to "II.A",
        BoeSectionDto.II_B to "II.B",
        BoeSectionDto.III to "III",
        BoeSectionDto.IV to "IV",
        BoeSectionDto.V_A to "V.A",
        BoeSectionDto.V_B to "V.B",
        BoeSectionDto.V_C to "V.C",
    )

    @Test
    fun categoriasUseStableSnakeCaseWireTokens() {
        assertEquals(11, CategoryDto.entries.size)
        tokensEsperados.forEach { (category, token) ->
            val json = DatoPublicoJson.encodeToString(CategoryDto.serializer(), category)
            assertEquals("\"$token\"", json)
            assertEquals(
                category,
                DatoPublicoJson.decodeFromString(CategoryDto.serializer(), json),
            )
        }
    }

    @Test
    fun categoriasHaveUniqueTokens() {
        assertEquals(tokensEsperados.size, tokensEsperados.values.toSet().size)
    }

    @Test
    fun seccionBoeUsesOfficialNotation() {
        assertEquals(8, BoeSectionDto.entries.size)
        tokensSeccion.forEach { (section, token) ->
            val json = DatoPublicoJson.encodeToString(BoeSectionDto.serializer(), section)
            assertEquals("\"$token\"", json)
            assertEquals(
                section,
                DatoPublicoJson.decodeFromString(BoeSectionDto.serializer(), json),
            )
        }
    }
}
