package es.aviferdev.datopublico.backend.rag.summary

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.persistence.GenerationUsageEntity
import es.aviferdev.datopublico.backend.persistence.GenerationUsageRepository
import es.aviferdev.datopublico.backend.persistence.SummaryCacheEntity
import es.aviferdev.datopublico.backend.persistence.SummaryCacheRepository
import es.aviferdev.datopublico.backend.rag.generation.TokenUsage

/**
 * Resultado **interno** de generar con caché (sin sufijo: no es un DTO de
 * transporte).
 *
 * @property result resultado de la generación ([CitizenSummaryResult.Valid] o
 *   [CitizenSummaryResult.Invalid]); en un acierto de caché siempre es `Valid`.
 * @property cacheHit `true` si el resumen vino de la caché (no se invocó al
 *   proveedor); `false` si se generó de nuevo.
 */
data class CachedSummaryResult(
    val result: CitizenSummaryResult,
    val cacheHit: Boolean,
)

/**
 * Servicio de **generación del resumen ciudadano con caché** y control de coste
 * (FT00021).
 *
 * Envuelve un [CitizenSummaryGenerator] (FT00020) sin reescribir su pipeline:
 * consulta la caché **antes** de invocar al proveedor. En un **acierto** devuelve
 * el resumen cacheado sin llamar a OpenCode y registra un uso con `cache_hit =
 * true` y 0 tokens; en un **fallo** genera, registra el uso real (los tokens del
 * proveedor o 0), **cachea solo si el resumen es `Valid`** y lo devuelve.
 *
 * Es una **librería sin wiring** (no se construye en `Application.kt` ni en el
 * job). El **modelo** se inyecta por constructor (en producción, de
 * `OpenCodeConfig`): no se lee de la config aquí, para no acoplar la caché a
 * OpenCode.
 *
 * @param generator servicio de generación a envolver.
 * @param cache repositorio de la caché del resumen.
 * @param usage repositorio del registro de consumo de tokens.
 * @param model nombre del modelo de generación (parte de la clave de caché).
 * @param promptVersion versión del prompt (parte de la clave de caché).
 */
class CachedCitizenSummaryGenerator(
    private val generator: CitizenSummaryGenerator,
    private val cache: SummaryCacheRepository,
    private val usage: GenerationUsageRepository,
    private val model: String,
    private val promptVersion: String = CitizenSummaryPromptBuilder.PROMPT_VERSION,
) {

    /**
     * Genera (o reutiliza) el resumen ciudadano de [publication].
     *
     * @param publication publicación de origen; debe tener `categoria`.
     * @param limit número máximo de fragmentos a recuperar (`>= 1`).
     * @return el resultado con `cacheHit` y, en un acierto, el resumen cacheado.
     * @throws IllegalArgumentException si [publication] no tiene categoría o la
     *   clave de caché no es válida (fail-fast).
     * @throws es.aviferdev.datopublico.backend.rag.generation.TextGenerationException
     *   si el proveedor falla.
     * @throws SummaryGenerationException si la salida del proveedor no es
     *   interpretable.
     */
    suspend fun generate(
        publication: Publicacion,
        limit: Int = CitizenSummaryGenerator.DEFAULT_LIMIT,
    ): CachedSummaryResult {
        val cacheKey = SummaryCacheKey.of(publication.id, model, promptVersion)
        val cached = cache.find(cacheKey)
        return if (cached != null) {
            hit(cached, cacheKey)
        } else {
            miss(publication, limit, cacheKey)
        }
    }

    /** Acierto: registra un uso con `cache_hit = true` y devuelve lo cacheado. */
    private fun hit(cached: SummaryCacheEntity, cacheKey: String): CachedSummaryResult {
        usage.record(usageOf(cached.publicationId, cacheKey, cacheHit = true, tokenUsage = null))
        return CachedSummaryResult(CitizenSummaryResult.Valid(cached.summary), cacheHit = true)
    }

    /** Fallo: genera, registra el uso real y cachea solo si el resumen es `Valid`. */
    private suspend fun miss(
        publication: Publicacion,
        limit: Int,
        cacheKey: String,
    ): CachedSummaryResult {
        val result = generator.generate(publication, limit)
        usage.record(usageOf(publication.id, cacheKey, cacheHit = false, tokenUsage = result.usage))
        cacheIfValid(result, publication.id, cacheKey)
        return CachedSummaryResult(result, cacheHit = false)
    }

    /** Guarda el resumen en la caché solo cuando cumple el contrato (`Valid`). */
    private fun cacheIfValid(result: CitizenSummaryResult, publicationId: String, cacheKey: String) {
        if (result is CitizenSummaryResult.Valid) {
            cache.put(SummaryCacheEntity(cacheKey, publicationId, model, promptVersion, result.summary))
        }
    }

    /** Construye el registro de uso; sin tokens del proveedor se registran 0. */
    private fun usageOf(
        publicationId: String,
        cacheKey: String,
        cacheHit: Boolean,
        tokenUsage: TokenUsage?,
    ): GenerationUsageEntity = GenerationUsageEntity(
        publicationId = publicationId,
        cacheKey = cacheKey,
        model = model,
        promptTokens = tokenUsage?.promptTokens ?: 0,
        completionTokens = tokenUsage?.completionTokens ?: 0,
        totalTokens = tokenUsage?.totalTokens ?: 0,
        cacheHit = cacheHit,
    )
}

/** Uso de tokens de un resultado, lo aporte o no el proveedor. */
private val CitizenSummaryResult.usage: TokenUsage?
    get() = when (this) {
        is CitizenSummaryResult.Valid -> usage
        is CitizenSummaryResult.Invalid -> usage
    }
