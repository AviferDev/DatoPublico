package es.aviferdev.datopublico.backend.rag.summary

import es.aviferdev.datopublico.backend.ingesta.publication.Publicacion
import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.persistence.FragmentRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.PublicationRepository
import es.aviferdev.datopublico.backend.persistence.PublicationRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.ReviewQueueRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.SummaryRepository
import es.aviferdev.datopublico.backend.persistence.SummaryRepositoryJdbc
import es.aviferdev.datopublico.backend.rag.embeddings.E5EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingConfig
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.generation.OpenCodeConfig
import es.aviferdev.datopublico.backend.rag.generation.OpenCodeTextGenerationProvider
import es.aviferdev.datopublico.backend.rag.guardrails.SummaryGuardrail
import es.aviferdev.datopublico.backend.rag.indexing.FragmentIndexer
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter
import es.aviferdev.datopublico.backend.rag.review.SummaryDispositionService
import es.aviferdev.datopublico.backend.rag.review.SummaryReviewOutcome
import es.aviferdev.datopublico.model.SummaryDto
import io.ktor.client.HttpClient
import javax.sql.DataSource

/**
 * Colaboradores del pipeline de generación y publicación de resúmenes ciudadanos.
 *
 * Es un **contenedor inmutable** que agrupa, ya construidos sobre el **mismo**
 * pool JDBC y el **mismo** proveedor de embeddings, los repositorios, el
 * indexador de fragmentos, la recuperación híbrida, el generador ciudadano, los
 * guardrails y el servicio de disposición. La lógica vive en las funciones de
 * este fichero para que cada una sea pequeña y con un solo punto de salida.
 *
 * @property publications repositorio de publicaciones (lectura por fecha).
 * @property summaries repositorio del resumen (detectar uno ya generado con IA).
 * @property indexer indexador idempotente de fragmentos con embeddings de pasaje.
 * @property hybridSearch recuperación híbrida que da contexto al guardrail.
 * @property generator generador del resumen ciudadano (recuperación + OpenCode).
 * @property guardrail guardrails anti-alucinación y de fuente oficial.
 * @property disposition disposición del resumen (publicar, encolar o descartar).
 */
internal class SummaryGenerationPipeline(
    val publications: PublicationRepository,
    val summaries: SummaryRepository,
    val indexer: FragmentIndexer,
    val hybridSearch: HybridSearch,
    val generator: CitizenSummaryGenerator,
    val guardrail: SummaryGuardrail,
    val disposition: SummaryDispositionService,
)

/**
 * Construye el pipeline sobre el [dataSource] y el [provider] compartidos,
 * reutilizando **una sola** instancia de [HybridSearch] para el generador y para
 * el contexto del guardrail.
 *
 * @param dataSource pool JDBC único del proceso.
 * @param provider proveedor de embeddings E5 ya cargado.
 * @param openCode configuración del proveedor de texto (modelo/temperatura).
 * @param client cliente HTTP de OpenCode (se cierra fuera, en el llamante).
 */
internal fun createPipeline(
    dataSource: DataSource,
    provider: EmbeddingProvider,
    openCode: OpenCodeConfig,
    client: HttpClient,
): SummaryGenerationPipeline {
    val fragments = FragmentRepositoryJdbc(dataSource)
    val hybridSearch = HybridSearch(provider, fragments)
    return SummaryGenerationPipeline(
        publications = PublicationRepositoryJdbc(dataSource),
        summaries = SummaryRepositoryJdbc(dataSource),
        indexer = FragmentIndexer(provider, fragments),
        hybridSearch = hybridSearch,
        generator = CitizenSummaryGenerator(
            hybridSearch = hybridSearch,
            textGenerationProvider = OpenCodeTextGenerationProvider(client, openCode),
        ),
        guardrail = SummaryGuardrail(),
        disposition = SummaryDispositionService(
            summaries = SummaryRepositoryJdbc(dataSource),
            reviewQueue = ReviewQueueRepositoryJdbc(dataSource),
        ),
    )
}

/**
 * Procesa una publicación: se salta las que ya tienen un resumen real (con
 * confianza persistida) y, en caso contrario, genera, valida, guarda y dispone.
 */
internal suspend fun processPublication(
    pipeline: SummaryGenerationPipeline,
    publication: Publicacion,
): SummaryOutcome {
    val outcome = if (hasRealSummary(pipeline, publication)) {
        SummaryOutcome.ALREADY_DONE
    } else {
        generateOutcome(pipeline, publication)
    }
    return outcome
}

/** ¿Tiene [publication] un resumen ya generado por el pipeline (confianza no nula)? */
internal fun hasRealSummary(
    pipeline: SummaryGenerationPipeline,
    publication: Publicacion,
): Boolean = pipeline.summaries.findByPublication(publication.id)?.confidence != null

/** Indexa, recupera contexto, genera y traduce el resultado de la generación. */
private suspend fun generateOutcome(
    pipeline: SummaryGenerationPipeline,
    publication: Publicacion,
): SummaryOutcome {
    pipeline.indexer.index(publication)
    val context = contextFor(pipeline, publication)
    return when (val generated = pipeline.generator.generate(publication)) {
        is CitizenSummaryResult.Invalid -> SummaryOutcome.INVALID
        is CitizenSummaryResult.Valid -> disposeSummary(pipeline, publication, generated.summary, context)
    }
}

/** Evalúa los guardrails del resumen generado y lo dispone (publicar/encolar/descartar). */
private suspend fun disposeSummary(
    pipeline: SummaryGenerationPipeline,
    publication: Publicacion,
    summary: SummaryDto,
    context: List<FragmentEntity>,
): SummaryOutcome {
    val guardrail = pipeline.guardrail.evaluate(publication, summary, context)
    return when (pipeline.disposition.dispose(publication, summary, guardrail)) {
        is SummaryReviewOutcome.Published -> SummaryOutcome.PUBLISHED
        is SummaryReviewOutcome.Queued -> SummaryOutcome.QUEUED
        is SummaryReviewOutcome.Discarded -> SummaryOutcome.DISCARDED
    }
}

/** Recupera los fragmentos de contexto de [publication] (mismo filtro que el generador). */
private fun contextFor(
    pipeline: SummaryGenerationPipeline,
    publication: Publicacion,
): List<FragmentEntity> =
    pipeline.hybridSearch.search(publication.title, filterFor(publication), CONTEXT_LIMIT)
        .map { match -> match.fragment }

/** Filtro de recuperación por los metadatos de [publication]. */
private fun filterFor(publication: Publicacion): SearchFilter = SearchFilter(
    publishedFrom = publication.publicationDate,
    publishedTo = publication.publicationDate,
    category = publication.category,
    section = publication.section,
    organization = publication.issuingBody,
)

/**
 * Carga el proveedor E5 real resolviendo la configuración desde el entorno
 * (`EMBEDDING_MODEL_PATH`/`EMBEDDING_TOKENIZER_PATH`, fail-fast si faltan o no
 * existen los ficheros).
 */
internal fun createEmbeddingProvider(): E5EmbeddingProvider {
    val config = EmbeddingConfig.fromEnv()
    return E5EmbeddingProvider.from(config.modelPath, config.tokenizerPath, config.maxTokens)
}

/** Número de fragmentos de contexto (el mismo que usa el generador). */
private const val CONTEXT_LIMIT = CitizenSummaryGenerator.DEFAULT_LIMIT
