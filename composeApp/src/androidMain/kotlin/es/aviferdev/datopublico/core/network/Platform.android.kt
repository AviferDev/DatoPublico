package es.aviferdev.datopublico.core.network

import java.time.LocalDate

/**
 * `actual` de [platformApiBaseUrl] en Android (FT00035).
 *
 * El emulador accede a la máquina anfitriona por el alias `10.0.2.2`; un
 * dispositivo físico necesitaría la IP del host (los valores de producción son
 * FT00038–FT00040). El tráfico claro solo se permite a este host mediante
 * `network_security_config.xml`.
 */
actual fun platformApiBaseUrl(): String = ANDROID_EMULATOR_BASE_URL

/**
 * `actual` de [currentIsoDate] en Android: `java.time.LocalDate` (API 26+, la
 * `minSdk` del proyecto).
 */
actual fun currentIsoDate(): String = LocalDate.now().toString()

/** URL base del backend en el emulador de Android. */
private const val ANDROID_EMULATOR_BASE_URL = "http://10.0.2.2:8080"
