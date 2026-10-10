package es.aviferdev.datopublico.model

import es.aviferdev.datopublico.serialization.DatoPublicoJson
import es.aviferdev.datopublico.validation.CitizenSummaryValidator
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CitizenSummaryContractTest {

    private val fullSummary = ResumenDto(
        queCambia = "Se convocan 120 plazas de auxiliar administrativo.",
        aQuienAfecta = "Personas con título de ESO o equivalente.",
        cifrasClave = listOf("120 plazas", "20 días hábiles"),
        fuenteOficial = "https://www.boe.es/diario_boe/txt.php?id=BOE-B-2026-1234",
        avisoIA = true,
        plazo = PlazoDto(fechaLimite = "2026-10-30", descripcion = "20 días hábiles"),
    )

    @Test
    fun completeEmploymentSummaryRoundTripsAndValidates() {
        val json = DatoPublicoJson.encodeToString(ResumenDto.serializer(), fullSummary)
        val decoded = DatoPublicoJson.decodeFromString(ResumenDto.serializer(), json)

        assertEquals(fullSummary, decoded)
        assertEquals(json, DatoPublicoJson.encodeToString(ResumenDto.serializer(), decoded))
        assertTrue(CitizenSummaryValidator.validate(decoded, CategoriaDto.OPOSICIONES_Y_EMPLEO_PUBLICO).isValid)
    }

    @Test
    fun summaryJsonKeepsFrozenWireKeys() {
        val json = DatoPublicoJson.encodeToString(ResumenDto.serializer(), fullSummary)

        val keys = DatoPublicoJson.parseToJsonElement(json).jsonObject.keys

        assertEquals(
            setOf("queCambia", "aQuienAfecta", "cifrasClave", "fuenteOficial", "avisoIA", "plazo"),
            keys,
        )
    }

    @Test
    fun summaryJsonKeepsFrozenDeadlineKeys() {
        val json = DatoPublicoJson.encodeToString(ResumenDto.serializer(), fullSummary)

        val deadline = DatoPublicoJson.parseToJsonElement(json).jsonObject.getValue("plazo").jsonObject

        assertEquals(setOf("fechaLimite", "descripcion"), deadline.keys)
    }
}
