package es.aviferdev.datopublico.backend.rag.guardrails

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Prueba **opt-in** de alcanzabilidad real de una fuente oficial del BOE
 * (escenario 10 del spec).
 *
 * Se salta por defecto (`BOE_LIVE_TEST != 1`) para que el gate y la CI **no**
 * abran sockets. Para ejecutarla:
 *
 * ```
 * BOE_LIVE_TEST=1 ./gradlew :backend:test --tests '*SummaryGuardrailLiveTest'
 * ```
 */
class SummaryGuardrailLiveTest {

    @Test
    fun `the official BOE source is reachable`() = runTest {
        if (System.getenv("BOE_LIVE_TEST") != "1") {
            println("SummaryGuardrailLiveTest omitido: exporta BOE_LIVE_TEST=1 para el test real.")
        } else {
            runLive()
        }
    }

    /** Comprueba la alcanzabilidad real y afirma que el BOE responde 2xx/3xx. */
    private suspend fun runLive() {
        val client = sourceReachabilityHttpClient()
        try {
            val reachable = HttpSourceReachability(client).isReachable(URL)
            assertTrue(reachable, "La URL oficial del BOE no respondió alcanzable: $URL")
        } finally {
            client.close()
        }
    }

    private companion object {
        /** URL oficial real de una publicación del BOE. */
        const val URL = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2024-87"
    }
}
