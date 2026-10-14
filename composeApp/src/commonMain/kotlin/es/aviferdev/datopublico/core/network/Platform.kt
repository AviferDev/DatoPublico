package es.aviferdev.datopublico.core.network

/**
 * Identidad de plataforma del cliente HTTP (FT00035).
 *
 * `commonMain` **no** puede cablear una URL ni leer el reloj: ambos dependen de
 * la plataforma. Estas funciones `expect` se resuelven con un `actual` por
 * *source set* (`androidMain`, `jvmMain`, `iosMain`, `wasmJsMain`), de modo que el
 * código común solo conoce la **forma** del dato.
 */

/**
 * URL base de la API pública del backend para esta plataforma.
 *
 * Android apunta al *loopback* del emulador (`10.0.2.2`, que resuelve al host);
 * iOS/JVM/Wasm usan `localhost`. Son valores de **desarrollo**: los de producción
 * los fijan las features de empaquetado (FT00038–FT00040). **Nunca** se cablea una
 * URL en `commonMain`.
 *
 * @return la URL base, sin barra final (p. ej. el host de desarrollo con su
 *   puerto).
 */
expect fun platformApiBaseUrl(): String

/**
 * Fecha **local** del dispositivo en ISO-8601 (`aaaa-mm-dd`), sin `java.time` en
 * el código común (rompería iOS/Wasm).
 *
 * Es la fecha que exige el endpoint `GET /v1/feed`; se calcula con la zona horaria
 * del dispositivo (un dispositivo en otra zona puede pedir un día distinto al del
 * servidor, aceptable en el MVP).
 *
 * @return la fecha de hoy en ISO-8601.
 */
expect fun currentIsoDate(): String
