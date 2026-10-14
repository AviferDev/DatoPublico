package es.aviferdev.datopublico.core.network

import kotlin.js.js

/**
 * `actual` de [platformApiBaseUrl] en Web/Wasm (FT00035).
 *
 * El backend de desarrollo corre en la máquina que sirve la app (`localhost`); el
 * CORS de la API pública lo resuelve el backend (FT00030).
 */
actual fun platformApiBaseUrl(): String = WASM_BASE_URL

/**
 * `actual` de [currentIsoDate] en Web/Wasm: el `Date` local del navegador,
 * compuesto a mano en `aaaa-mm-dd` mediante interop JS (sin `java.time` ni
 * dependencias nuevas).
 */
actual fun currentIsoDate(): String = jsLocalIsoDate()

/**
 * Fecha local del navegador en `aaaa-mm-dd`, calculada en JavaScript.
 *
 * Se implementa con `js(...)` (no con `kotlin.js.Date`, que no está disponible en
 * Wasm-JS) para respetar la zona horaria del dispositivo sin añadir
 * `kotlinx-datetime`. En Wasm, `js(...)` debe ser la única expresión de una
 * función de nivel de paquete con tipo de retorno explícito.
 */
private fun jsLocalIsoDate(): String = js(
    "(() => {" +
        " const now = new Date();" +
        " const month = String(now.getMonth() + 1).padStart(2, '0');" +
        " const day = String(now.getDate()).padStart(2, '0');" +
        " return now.getFullYear() + '-' + month + '-' + day;" +
        "})()"
)

/** URL base del backend en Web/Wasm (desarrollo local). */
private const val WASM_BASE_URL = "http://localhost:8080"
