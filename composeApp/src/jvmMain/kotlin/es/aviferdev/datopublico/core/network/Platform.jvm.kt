package es.aviferdev.datopublico.core.network

import java.time.LocalDate

/**
 * `actual` de [platformApiBaseUrl] en JVM (target de tests/dev de `:composeApp`).
 *
 * El backend corre en la misma máquina, de ahí `localhost`.
 */
actual fun platformApiBaseUrl(): String = JVM_BASE_URL

/**
 * `actual` de [currentIsoDate] en JVM: `java.time.LocalDate`.
 */
actual fun currentIsoDate(): String = LocalDate.now().toString()

/** URL base del backend en JVM (desarrollo local). */
private const val JVM_BASE_URL = "http://localhost:8080"
