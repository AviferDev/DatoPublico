package es.aviferdev.datopublico.core.network

import kotlin.js.js

/**
 * `actual` de [platformApiBaseUrl] en Web/Wasm (FT00035).
 *
 * La URL base de la Web **no** está fija en el código: se inyecta en build time
 * con la propiedad Gradle `apiBaseUrl` (`-PapiBaseUrl=<url>`), que genera la
 * constante [API_BASE_URL] en `wasmJsMain` (FT00039-PT1). Sin la propiedad, la
 * constante vale `http://localhost:8080` (el backend de desarrollo corre en la
 * máquina que sirve la app); el CORS de la API pública lo resuelve el backend
 * (FT00030).
 */
actual fun platformApiBaseUrl(): String = API_BASE_URL

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

