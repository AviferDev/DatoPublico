package es.aviferdev.datopublico.model

import es.aviferdev.datopublico.serialization.DatoPublicoJson
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoriaWireTest {

    private val tokensEsperados = mapOf(
        CategoriaDto.NORMAS_Y_LEGISLACION to "normas_y_legislacion",
        CategoriaDto.NOMBRAMIENTOS_Y_CESES to "nombramientos_y_ceses",
        CategoriaDto.OPOSICIONES_Y_EMPLEO_PUBLICO to "oposiciones_y_empleo_publico",
        CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS to "becas_subvenciones_y_ayudas",
        CategoriaDto.PREMIOS to "premios",
        CategoriaDto.CONVENIOS_Y_ACUERDOS to "convenios_y_acuerdos",
        CategoriaDto.EDUCACION_Y_PLANES_DE_ESTUDIO to "educacion_y_planes_de_estudio",
        CategoriaDto.MEDIO_AMBIENTE to "medio_ambiente",
        CategoriaDto.RECURSOS_Y_RESOLUCIONES to "recursos_y_resoluciones",
        CategoriaDto.INFORMACION_PUBLICA_Y_CONCESIONES to "informacion_publica_y_concesiones",
        CategoriaDto.OTRAS_DISPOSICIONES_Y_ANUNCIOS to "otras_disposiciones_y_anuncios",
    )

    private val tokensSeccion = mapOf(
        SeccionBoeDto.I to "I",
        SeccionBoeDto.II_A to "II.A",
        SeccionBoeDto.II_B to "II.B",
        SeccionBoeDto.III to "III",
        SeccionBoeDto.IV to "IV",
        SeccionBoeDto.V_A to "V.A",
        SeccionBoeDto.V_B to "V.B",
        SeccionBoeDto.V_C to "V.C",
    )

    @Test
    fun categoriasUseStableSnakeCaseWireTokens() {
        assertEquals(11, CategoriaDto.entries.size)
        tokensEsperados.forEach { (categoria, token) ->
            val json = DatoPublicoJson.encodeToString(CategoriaDto.serializer(), categoria)
            assertEquals("\"$token\"", json)
            assertEquals(
                categoria,
                DatoPublicoJson.decodeFromString(CategoriaDto.serializer(), json),
            )
        }
    }

    @Test
    fun categoriasHaveUniqueTokens() {
        assertEquals(tokensEsperados.size, tokensEsperados.values.toSet().size)
    }

    @Test
    fun seccionBoeUsesOfficialNotation() {
        assertEquals(8, SeccionBoeDto.entries.size)
        tokensSeccion.forEach { (seccion, token) ->
            val json = DatoPublicoJson.encodeToString(SeccionBoeDto.serializer(), seccion)
            assertEquals("\"$token\"", json)
            assertEquals(
                seccion,
                DatoPublicoJson.decodeFromString(SeccionBoeDto.serializer(), json),
            )
        }
    }
}
