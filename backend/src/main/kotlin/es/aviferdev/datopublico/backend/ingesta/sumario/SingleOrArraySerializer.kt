package es.aviferdev.datopublico.backend.ingesta.sumario

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Crea un serializador de `List<T>` que acepta que el JSON de origen traiga el
 * elemento como un **objeto único** o como una **lista**.
 *
 * El sumario del BOE no es homogéneo: `item`, `epigrafe`, `departamento` y otras
 * colecciones aparecen indistintamente en una u otra forma (verificado con los
 * fixtures reales `20261009` y `20240102`). Un `ListSerializer` estándar fallaría
 * con la forma de objeto; esto lo normaliza a lista.
 */
fun <T> singleOrArraySerializer(elementSerializer: KSerializer<T>): KSerializer<List<T>> {
    val listSerializer = ListSerializer(elementSerializer)
    return object : KSerializer<List<T>> {
        override val descriptor: SerialDescriptor = listSerializer.descriptor

        override fun deserialize(decoder: Decoder): List<T> {
            if (decoder !is JsonDecoder) return decoder.decodeSerializableValue(listSerializer)
            return when (val json = decoder.decodeJsonElement()) {
                is JsonArray -> json.map { decoder.json.decodeFromJsonElement(elementSerializer, it) }
                is JsonObject -> listOf(decoder.json.decodeFromJsonElement(elementSerializer, json))
                is JsonNull -> emptyList()
                else -> throw SerializationException(
                    "Se esperaba un objeto o un array JSON, pero llegó: $json"
                )
            }
        }

        override fun serialize(encoder: Encoder, value: List<T>) {
            encoder.encodeSerializableValue(listSerializer, value)
        }
    }
}

/** `diario`: colección de días del sumario (lista u objeto). */
object DiarioListSerializer :
    KSerializer<List<BoeDiarioDto>> by singleOrArraySerializer(BoeDiarioDto.serializer())

/** `seccion`: colección de secciones (lista u objeto). */
object SeccionListSerializer :
    KSerializer<List<BoeSeccionDto>> by singleOrArraySerializer(BoeSeccionDto.serializer())

/** `departamento`: colección de organismos (lista u objeto; objeto real en `20240102`). */
object DepartamentoListSerializer :
    KSerializer<List<BoeDepartamentoDto>> by singleOrArraySerializer(BoeDepartamentoDto.serializer())

/** `epigrafe`: colección de epígrafes (lista u objeto). */
object EpigrafeListSerializer :
    KSerializer<List<BoeEpigrafeDto>> by singleOrArraySerializer(BoeEpigrafeDto.serializer())

/** `item`: colección de entradas (objeto único o lista; forma mixta en ambos fixtures). */
object ItemListSerializer :
    KSerializer<List<BoeSumarioItemDto>> by singleOrArraySerializer(BoeSumarioItemDto.serializer())
