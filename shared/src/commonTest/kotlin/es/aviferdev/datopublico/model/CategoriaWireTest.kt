package es.aviferdev.datopublico.model

import es.aviferdev.datopublico.serialization.DatoPublicoJson
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoriaWireTest {

    private val tokensEsperados = mapOf(
        Categoria.NORMAS_Y_LEGISLACION to "normas_y_legislacion",
        Categoria.NOMBRAMIENTOS_Y_CESES to "nombramientos_y_ceses",
        Categoria.OPOSICIONES_Y_EMPLEO_PUBLICO to "oposiciones_y_empleo_publico",
        Categoria.BECAS_SUBVENCIONES_Y_AYUDAS to "becas_subvenciones_y_ayudas",
        Categoria.PREMIOS to "premios",
        Categoria.CONVENIOS_Y_ACUERDOS to "convenios_y_acuerdos",
        Categoria.EDUCACION_Y_PLANES_DE_ESTUDIO to "educacion_y_planes_de_estudio",
        Categoria.MEDIO_AMBIENTE to "medio_ambiente",
        Categoria.RECURSOS_Y_RESOLUCIONES to "recursos_y_resoluciones",
        Categoria.INFORMACION_PUBLICA_Y_CONCESIONES to "informacion_publica_y_concesiones",
        Categoria.OTRAS_DISPOSICIONES_Y_ANUNCIOS to "otras_disposiciones_y_anuncios",
    )

    private val tokensSeccion = mapOf(
        SeccionBoe.I to "I",
        SeccionBoe.II_A to "II.A",
        SeccionBoe.II_B to "II.B",
        SeccionBoe.III to "III",
        SeccionBoe.V_B to "V.B",
    )

    @Test
    fun categoriasUseStableSnakeCaseWireTokens() {
        assertEquals(11, Categoria.entries.size)
        tokensEsperados.forEach { (categoria, token) ->
            val json = DatoPublicoJson.encodeToString(Categoria.serializer(), categoria)
            assertEquals("\"$token\"", json)
            assertEquals(
                categoria,
                DatoPublicoJson.decodeFromString(Categoria.serializer(), json),
            )
        }
    }

    @Test
    fun categoriasHaveUniqueTokens() {
        assertEquals(tokensEsperados.size, tokensEsperados.values.toSet().size)
    }

    @Test
    fun seccionBoeUsesOfficialNotation() {
        assertEquals(5, SeccionBoe.entries.size)
        tokensSeccion.forEach { (seccion, token) ->
            val json = DatoPublicoJson.encodeToString(SeccionBoe.serializer(), seccion)
            assertEquals("\"$token\"", json)
            assertEquals(
                seccion,
                DatoPublicoJson.decodeFromString(SeccionBoe.serializer(), json),
            )
        }
    }
}
