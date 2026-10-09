package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sección del BOE de la que procede una publicacion ingerida.
 *
 * El token de wire conserva la notación oficial (`II.A`, `II.B`, `V.B`), que
 * difiere del nombre Kotlin (`II_A`, `II_B`, `V_B`). Es contrato entre backend y
 * cliente.
 */
@Serializable
enum class SeccionBoe {
    @SerialName("I")
    I,

    @SerialName("II.A")
    II_A,

    @SerialName("II.B")
    II_B,

    @SerialName("III")
    III,

    @SerialName("V.B")
    V_B,
}
