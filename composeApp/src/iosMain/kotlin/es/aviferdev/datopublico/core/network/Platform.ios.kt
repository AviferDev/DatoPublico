package es.aviferdev.datopublico.core.network

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

/**
 * `actual` de [platformApiBaseUrl] en iOS (FT00035).
 *
 * El backend de desarrollo corre en la máquina anfitriona (`localhost`). El
 * permiso ATS para tráfico claro vive en el proyecto Xcode (FT00040); el
 * framework queda listo.
 */
actual fun platformApiBaseUrl(): String = IOS_BASE_URL

/**
 * `actual` de [currentIsoDate] en iOS con `NSDateFormatter` en locale POSIX
 * (`en_US_POSIX`), para que el patrón `aaaa-mm-dd` no dependa del locale del
 * dispositivo.
 */
actual fun currentIsoDate(): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = ISO_DATE_PATTERN
    formatter.locale = NSLocale(localeIdentifier = POSIX_LOCALE_ID)
    return formatter.stringFromDate(NSDate())
}

/** URL base del backend en iOS (desarrollo local). */
private const val IOS_BASE_URL = "http://localhost:8080"

/** Patrón ISO-8601 de fecha (sin hora). */
private const val ISO_DATE_PATTERN = "yyyy-MM-dd"

/** Locale neutro para que el patrón de fecha sea estable. */
private const val POSIX_LOCALE_ID = "en_US_POSIX"
