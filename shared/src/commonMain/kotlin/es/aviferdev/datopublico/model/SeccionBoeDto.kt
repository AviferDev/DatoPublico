package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sección del BOE de la que procede una publicacion ingerida.
 *
 * La 1.0.0 cubre **todo el sumario del BOE**: las ocho secciones (I, II.A, II.B,
 * III, IV, V.A, V.B y V.C). Ninguna se excluye.
 *
 * El token de wire conserva la notación oficial (`II.A`, `II.B`, `V.A`, `V.B`,
 * `V.C`), que difiere del nombre Kotlin (`II_A`, `II_B`, `V_A`, `V_B`, `V_C`). Es
 * contrato entre backend y cliente.
 */
@Serializable
enum class SeccionBoeDto {
    @SerialName("I")
    I,

    @SerialName("II.A")
    II_A,

    @SerialName("II.B")
    II_B,

    @SerialName("III")
    III,

    @SerialName("IV")
    IV,

    @SerialName("V.A")
    V_A,

    @SerialName("V.B")
    V_B,

    @SerialName("V.C")
    V_C,
}
