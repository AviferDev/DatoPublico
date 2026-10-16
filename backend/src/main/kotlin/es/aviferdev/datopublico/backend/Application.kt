package es.aviferdev.datopublico.backend

import es.aviferdev.datopublico.backend.chat.ChatService
import es.aviferdev.datopublico.backend.chat.PublicationCitationResolver
import es.aviferdev.datopublico.backend.feed.DailyFeedService
import es.aviferdev.datopublico.backend.infra.AppConfig
import es.aviferdev.datopublico.backend.infra.CorsSettings
import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.backend.infra.ProxySettings
import es.aviferdev.datopublico.backend.infra.RateLimitSettings
import es.aviferdev.datopublico.backend.ingestion.job.IngestionConfig
import es.aviferdev.datopublico.backend.ingestion.job.DailyIngestionJob
import es.aviferdev.datopublico.backend.ingestion.job.IngestionRunState
import es.aviferdev.datopublico.backend.ingestion.job.IngestionSchedule
import es.aviferdev.datopublico.backend.ingestion.job.IngestionScheduler
import es.aviferdev.datopublico.backend.ingestion.job.IngestionWatchdog
import es.aviferdev.datopublico.backend.ingestion.publication.BoePublicationHttpParser
import es.aviferdev.datopublico.backend.ingestion.publication.BoeTextHttpClient
import es.aviferdev.datopublico.backend.ingestion.publication.boeTextHttpClient
import es.aviferdev.datopublico.backend.ingestion.summary.BoeSummaryHttpClient
import es.aviferdev.datopublico.backend.ingestion.summary.boeSummaryHttpClient
import es.aviferdev.datopublico.backend.persistence.FeedRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.FragmentRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.PublicationRepositoryJdbc
import es.aviferdev.datopublico.backend.persistence.SummaryRepositoryJdbc
import es.aviferdev.datopublico.backend.plugins.configureCors
import es.aviferdev.datopublico.backend.plugins.configureErrorHandling
import es.aviferdev.datopublico.backend.plugins.configureForwardedHeaders
import es.aviferdev.datopublico.backend.plugins.configureMonitoring
import es.aviferdev.datopublico.backend.plugins.configureRateLimit
import es.aviferdev.datopublico.backend.plugins.configureRouting
import es.aviferdev.datopublico.backend.plugins.configureSerialization
import es.aviferdev.datopublico.backend.publication.PublicationDetailService
import es.aviferdev.datopublico.backend.rag.chat.RagChatService
import es.aviferdev.datopublico.backend.rag.embeddings.E5EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingConfig
import es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingProvider
import es.aviferdev.datopublico.backend.rag.generation.OpenCodeConfig
import es.aviferdev.datopublico.backend.rag.generation.OpenCodeTextGenerationProvider
import es.aviferdev.datopublico.backend.rag.generation.TextGenerationException
import es.aviferdev.datopublico.backend.rag.generation.TextGenerationProvider
import es.aviferdev.datopublico.backend.rag.generation.openCodeHttpClient
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.backend.search.SearchService
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import javax.sql.DataSource
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Composition root del backend: lee la configuración del entorno y arranca Netty.
 *
 * El arranque no depende de la base de datos ni de servicios externos: si la
 * configuración de BD no resuelve, no se crea el pool, `/feed` no se registra y el
 * servidor sigue sirviendo `/health`.
 */
fun main() {
    val config = AppConfig.fromEnv()
    embeddedServer(
        Netty,
        port = config.port,
        host = config.host,
        module = Application::module,
    ).start(wait = true)
}

/**
 * Módulo Ktor: serialización, logging de llamadas, **endurecimiento HTTP**
 * (CORS, rate limiting y cuerpo de error tipado, FT00030), pool JDBC **único**
 * compartido por el feed, el detalle, la búsqueda, el chat y la ingesta,
 * proveedores condicionales (embeddings y texto), rutas e ingesta condicional.
 *
 * El pool lo crea el composition root (una sola vez) y lo cierra **una sola vez**
 * en `ApplicationStopped`; ni las rutas ni la ingesta lo cierran. El proveedor de
 * embeddings y el cliente HTTP de OpenCode se cablean de forma **condicional**
 * (sin modelo o sin clave quedan a `null`, las rutas `/buscar`/`/chat` no se
 * registran y el arranque no falla) y se cierran también una sola vez al parar.
 *
 * Los tres plugins de endurecimiento (CORS, errores y rate limiting) se instalan
 * **siempre** y no dependen de la BD, el modelo ni la clave: el arranque parcial
 * (solo salud) sigue funcionando y el rate limiting solo se aplica a las rutas de
 * dominio (`applyRateLimits = true`).
 */
fun Application.module() {
    configureSerialization()
    configureMonitoring()
    configureForwardedHeaders(ProxySettings.fromEnv())
    configureCors(CorsSettings.fromEnv())
    configureErrorHandling()
    configureRateLimit(RateLimitSettings.fromEnv())
    val dataSource = createDataSourceOrNull()
    val embeddingProvider = createEmbeddingProviderOrNull()
    val textRuntime = createTextGenerationOrNull()
    configureRouting(
        feedServiceOf(dataSource),
        detailServiceOf(dataSource),
        searchServiceOf(dataSource, embeddingProvider),
        chatServiceOf(dataSource, embeddingProvider, textRuntime),
        applyRateLimits = true,
    )
    configureIngestion(dataSource)
    monitorResources(dataSource, embeddingProvider, textRuntime)
}

/**
 * Crea el proveedor de embeddings locales (ONNX) de forma **condicional**, o
 * `null` si falta el modelo/tokenizador o su configuración. Un fallo **no** impide
 * el arranque: se registra un aviso y `/buscar` y `/chat` no se registran. El
 * proveedor se cierra una sola vez en `ApplicationStopped`.
 */
private fun Application.createEmbeddingProviderOrNull(): EmbeddingProvider? =
    try {
        val config = EmbeddingConfig.fromEnv()
        E5EmbeddingProvider.from(config.modelPath, config.tokenizerPath, config.maxTokens)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        log.warn("Proveedor de embeddings no disponible: {}", error.message)
        null
    }

/**
 * Crea el pool JDBC **único** del backend, o `null` si la configuración de base de
 * datos no resuelve (`POSTGRES_PASSWORD` ausente, etc.). Un fallo **no** impide el
 * arranque: sin pool, ni el feed, ni el detalle, ni la búsqueda ni el chat se
 * registran y la ingesta se omite.
 */
private fun Application.createDataSourceOrNull(): DataSource? =
    try {
        Database.createDataSource(DatabaseConfig.fromEnv())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        log.warn("Base de datos no configurada: {}", error.message)
        null
    }

/**
 * Programa el cierre **único** de los recursos compartidos al parar el servidor:
 * el pool JDBC, el proveedor de embeddings y el runtime de texto de OpenCode. Cada
 * recurso se libera una sola vez y solo si existe.
 */
private fun Application.monitorResources(
    dataSource: DataSource?,
    embeddingProvider: EmbeddingProvider?,
    textRuntime: TextGenerationRuntime?,
) {
    if (dataSource != null) {
        monitor.subscribe(ApplicationStopped) { closeDataSource(dataSource) }
    }
    if (embeddingProvider != null) {
        monitor.subscribe(ApplicationStopped) { embeddingProvider.close() }
    }
    if (textRuntime != null) {
        monitor.subscribe(ApplicationStopped) { textRuntime.close() }
    }
}

/** Construye el feed sobre el pool compartido, o `null` si no hay base de datos. */
private fun feedServiceOf(dataSource: DataSource?): DailyFeedService? =
    if (dataSource == null) null else DailyFeedService(FeedRepositoryJdbc(dataSource))

/**
 * Construye el detalle de publicación sobre el **mismo** pool compartido
 * (reutiliza [PublicationRepositoryJdbc] y [SummaryRepositoryJdbc]), o `null` si
 * no hay base de datos.
 */
private fun detailServiceOf(dataSource: DataSource?): PublicationDetailService? =
    if (dataSource == null) {
        null
    } else {
        PublicationDetailService(
            PublicationRepositoryJdbc(dataSource),
            SummaryRepositoryJdbc(dataSource),
        )
    }

/**
 * Construye el servicio de búsqueda sobre el **pool compartido** y el proveedor de
 * embeddings, o `null` si falta cualquiera de los dos (sin BD o sin modelo,
 * `/buscar` no se registra).
 */
private fun searchServiceOf(dataSource: DataSource?, embeddingProvider: EmbeddingProvider?): SearchService? =
    if (dataSource == null || embeddingProvider == null) {
        null
    } else {
        SearchService(
            hybridSearch = HybridSearch(embeddingProvider, FragmentRepositoryJdbc(dataSource)),
            feedRepository = FeedRepositoryJdbc(dataSource),
        )
    }

/**
 * Construye el **chat RAG** sobre el **mismo** pool compartido, o `null` si falta
 * cualquiera de sus dependencias (base de datos, proveedor de embeddings o
 * proveedor de texto): en ese caso `/chat` no se registra (`404`) y `/health`
 * sigue operativo.
 *
 * Reutiliza el pool único con `FragmentRepositoryJdbc` (recuperación) y
 * `PublicationRepositoryJdbc` (resolución de la cita) e **inyecta** la fuente
 * oficial con [PublicationCitationResolver]. El `limit` del contexto lo fija
 * [RagChatService] (no controlable por el cliente).
 */
private fun chatServiceOf(
    dataSource: DataSource?,
    embeddingProvider: EmbeddingProvider?,
    textRuntime: TextGenerationRuntime?,
): ChatService? =
    if (dataSource == null || embeddingProvider == null || textRuntime == null) {
        null
    } else {
        ChatService(
            RagChatService(
                hybridSearch = HybridSearch(embeddingProvider, FragmentRepositoryJdbc(dataSource)),
                textGenerationProvider = textRuntime.provider,
                citationResolver = PublicationCitationResolver(PublicationRepositoryJdbc(dataSource)),
            ),
        )
    }

/**
 * Crea el proveedor de texto de OpenCode de forma **condicional**, o `null` si
 * falta `OPENCODE_API_KEY` (fail-fast de [OpenCodeConfig.fromEnv]). La clave vive
 * **solo** en el servidor y nunca se registra. El runtime devuelto cierra el
 * `HttpClient` al parar el servidor.
 */
private fun Application.createTextGenerationOrNull(): TextGenerationRuntime? =
    try {
        val config = OpenCodeConfig.fromEnv()
        val client = openCodeHttpClient()
        TextGenerationRuntime(OpenCodeTextGenerationProvider(client, config), client)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: TextGenerationException) {
        log.warn("Proveedor de generación OpenCode no configurado: {}", error.message)
        null
    }

/** Cierra el pool si su implementación lo permite (HikariCP lo es). */
private fun closeDataSource(dataSource: DataSource) {
    (dataSource as? AutoCloseable)?.close()
}

/**
 * Cablea la ingesta de forma **condicional y no bloqueante**.
 *
 * Solo se arranca si `INGESTA_ENABLED` está activo **y** hay pool (la config de
 * BD resuelve). Cualquier fallo al construirla se registra y el servidor sigue
 * arrancando; al parar se cancela el bucle y se cierran los clientes HTTP (el
 * pool lo cierra el composition root).
 */
private fun Application.configureIngestion(dataSource: DataSource?) {
    val config = IngestionConfig.fromEnv()
    if (config.enabled && dataSource != null) {
        startIngestion(config, dataSource)
    } else {
        log.info("Ingesta no programada (INGESTA_ENABLED=false o sin base de datos)")
    }
}

/** Arranca la ingesta y programa su cierre; un fallo no impide el arranque. */
private fun Application.startIngestion(config: IngestionConfig, dataSource: DataSource) {
    try {
        val runtime = createIngestionRuntime(config, dataSource)
        monitor.subscribe(ApplicationStopped) { runtime.close() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        log.warn("Ingesta no arrancada: {}", error.message)
    }
}

/**
 * Construye los recursos de la ingesta (clientes HTTP, job, scheduler y
 * vigilante) sobre el [dataSource] compartido y lanza sus bucles. Si algo falla,
 * cierra los clientes ya creados y propaga (el pool lo cierra el composition
 * root).
 */
private fun Application.createIngestionRuntime(
    config: IngestionConfig,
    dataSource: DataSource,
): IngestionRuntime {
    val summaryHttp = boeSummaryHttpClient()
    val textHttp = boeTextHttpClient()
    return try {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val schedule = IngestionSchedule(config.times, config.zone)
        val runState = IngestionRunState()
        val scheduler = IngestionScheduler(
            schedule = schedule,
            job = DailyIngestionJob(
                summaryClient = BoeSummaryHttpClient(summaryHttp),
                publicationParser = BoePublicationHttpParser(BoeTextHttpClient(textHttp)),
                repository = PublicationRepositoryJdbc(dataSource),
            ),
            zone = config.zone,
            runState = runState,
        )
        val watchdog = IngestionWatchdog(
            schedule = schedule,
            runState = runState,
            grace = config.grace,
        )
        scheduler.start(scope)
        watchdog.start(scope)
        log.info(
            "Ingesta programada: horas={} zona={} gracia={}min",
            config.times,
            config.zone,
            config.grace.toMinutes(),
        )
        IngestionRuntime(scope, listOf(summaryHttp, textHttp))
    } catch (error: Exception) {
        summaryHttp.close()
        textHttp.close()
        throw error
    }
}

/**
 * Recursos vivos de la ingesta: el bucle del scheduler y los clientes HTTP.
 * [close] los libera en `ApplicationStopped`.
 */
private class IngestionRuntime(
    private val scope: CoroutineScope,
    private val httpClients: List<HttpClient>,
) : AutoCloseable {
    override fun close() {
        scope.cancel()
        httpClients.forEach(HttpClient::close)
    }
}

/**
 * Recursos vivos del proveedor de texto de OpenCode: el proveedor y su
 * `HttpClient`. [close] cierra el cliente una sola vez en `ApplicationStopped`; la
 * clave vive **solo** en el servidor y nunca se registra.
 */
private class TextGenerationRuntime(
    val provider: TextGenerationProvider,
    private val httpClient: HttpClient,
) : AutoCloseable {
    override fun close() {
        httpClient.close()
    }
}
