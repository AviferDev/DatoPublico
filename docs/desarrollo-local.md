# Desarrollo local

Guía mínima para trabajar en local con el cliente Compose Multiplatform, el
backend y la base de datos.

## Requisitos

- **JDK 21** (por ejemplo el JBR de Android Studio).
- **SDK de Android** para el cliente Android: exporta `ANDROID_HOME` o crea
  `local.properties` (ignorado por git) con
  `sdk.dir=/ruta/al/Android/sdk`.
- **Xcode** (solo en macOS) para enlazar el framework de iOS.
- **Docker** (Desktop o Colima) con Docker Compose para la base de datos.

## Cliente Compose Multiplatform (`:composeApp`)

La UI compartida vive en `commonMain` (`App()`); cada plataforma aporta **solo**
su entrypoint (`MainActivity` en Android, `main()` en Wasm,
`MainViewController()` en iOS). El target `jvm()` se conserva **solo** para
tests/dev.

```sh
# Android (APK de debug)
./gradlew :composeApp:assembleDebug
# → composeApp/build/outputs/apk/debug/composeApp-debug.apk

# Web (Wasm): distribución estática
./gradlew :composeApp:wasmJsBrowserDistribution
# → composeApp/build/dist/wasmJs/productionExecutable/ (index.html + *.wasm);
#   sirve ese directorio con un servidor estático y ábrelo en el navegador.

# iOS (solo macOS)
./gradlew :composeApp:compileKotlinIosSimulatorArm64
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
# → composeApp/build/bin/iosSimulatorArm64/debugFramework/ComposeApp.framework
```

Notas:

- El toolchain de Kotlin/JS-Wasm (Node, Yarn y Binaryen) se descarga a la caché
  de Gradle. Sus repositorios Ivy se declaran en `settings.gradle.kts`, que usa
  `PREFER_SETTINGS` porque el plugin los registra a nivel de proyecto.
- Los tests de Wasm en navegador (Karma/Chrome) quedan **fuera** del gate: el
  `SmokeTest` corre en `jvmTest`, en los tests unitarios de Android y en
  `iosSimulatorArm64Test`. No hace falta Chrome instalado.
- El gate (`./gradlew build`, `./gradlew test`, `.harness/init.sh`) no requiere
  Xcode ni navegador: en hosts no-Apple los targets iOS se ignoran.

## Arranque del backend

```sh
./gradlew :backend:run
```

Arranca Netty en `0.0.0.0:8080` **sin** base de datos: el healthcheck no depende
de PostgreSQL. Se detiene con `Ctrl+C`.

```sh
curl -s http://localhost:8080/health   # {"status":"ok"}
```

Sin base de datos (`POSTGRES_PASSWORD` ausente) el servidor sigue sirviendo
`/health` (200) y los endpoints `/feed`, `/publicacion/{id}`, `/buscar` y `/chat`
**no** se registran (404). Con base de datos, el composition root crea un **pool
JDBC único** que comparten el feed, el detalle, la búsqueda, el chat y la ingesta y
lo cierra una sola vez al parar (ver «Feed diario», «Detalle de publicación»,
«Búsqueda (`GET /buscar`)» y «Chat RAG (`POST /chat`)»). La búsqueda necesita
**además** el modelo E5; el chat, el modelo E5 y `OPENCODE_API_KEY`: sin cualquiera
de los tres la ruta `/chat` no se registra.

Variables de entorno (todas opcionales; un valor inválido aborta el arranque con
un mensaje claro):

- `SERVER_HOST` (por defecto `0.0.0.0`).
- `SERVER_PORT` (por defecto `8080`); `PORT` como alternativa.
- `APP_ENV` (por defecto `local`) y `LOG_LEVEL` (por defecto `INFO`).

Los logs son **JSON de una línea por evento en stdout** (Logback +
`logstash-logback-encoder`), con los campos `app` y `env` tomados de esas
variables. Sin telemetría ni destino externo.

## Cliente del sumario del BOE (`:backend`)

El paquete `es.aviferdev.datopublico.backend.ingesta.sumario` encapsula la API de
datos abiertos del BOE
(`GET https://www.boe.es/datosabiertos/api/boe/sumario/{YYYYMMDD}` con
`Accept: application/json`). Devuelve las entradas de **todo el sumario** (secciones
I, II.A, II.B, III, IV, V.A, V.B y V.C) con la URL html, xml y pdf de cada entrada,
y **no** se cablea en el arranque del backend.

Los tests con `MockEngine` (sin red) corren en el gate. La prueba contra la API
real es **opt-in** y queda fuera de `build`/`test`/`init.sh` y de la CI:

```sh
BOE_LIVE_TEST=1 ./gradlew :backend:test --tests '*BoeSumarioLiveTest'
```

Nota (macOS con el JBR de Android Studio): su `cacerts` no incluye la raíz
**FNMT-RCM** que firma `*.boe.es`, así que el test opt-in falla con
`SunCertPathBuilderException`. Añade esa raíz a un truststore y pásalo al JVM
(`JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=… -Djavax.net.ssl.trustStorePassword=…"`),
o ejecuta con un JDK cuyo truststore la incluya. `curl` sí valida el certificado
porque usa el llavero del sistema; el gate no se ve afectado.

## Parser del texto oficial del BOE (`:backend`)

El paquete `es.aviferdev.datopublico.backend.ingesta.publicacion` convierte cada
entrada del sumario en una `Publicacion` con su **texto completo** y metadatos,
descargados del **XML estructurado**
(`GET https://www.boe.es/diario_boe/xml.php?id=<identificador>` con
`Accept: application/xml`, cuerpo UTF-8). El XML es la **fuente de verdad** del
texto y los metadatos, no el HTML (`txt.php`) ni el PDF. Tampoco se cablea en el
arranque del backend.

Los tests del parser usan **fixtures XML reales** (uno por sección) y doblan la
red con `MockEngine`. Para (re)capturar un fixture:

```sh
curl -s 'https://www.boe.es/diario_boe/xml.php?id=BOE-A-2024-87' \
  -o backend/src/test/resources/boe/texto-IIA-2024-87.xml
```

La prueba contra el BOE real es **opt-in** y queda fuera del gate:

```sh
BOE_LIVE_TEST=1 ./gradlew :backend:test --tests '*BoePublicacionLiveTest'
```

Nota (macOS con el JBR de Android Studio): como con el sumario, el test opt-in
falla con `SunCertPathBuilderException` si el truststore del JVM no incluye la
raíz **FNMT-RCM** (misma limitación documentada arriba); `curl` sí valida porque
usa el llavero del sistema. El gate no se ve afectado.

## Clasificación por categoría y plazos (`:backend`)

El paquete `es.aviferdev.datopublico.backend.ingesta.categorizacion` añade, en
`PublicacionParser`, dos piezas **puras** (sin IA, red, BD ni reloj):

- `PublicationClassifier`: clasifica cada publicación en una de las **11
  categorías** curadas (`CategoriaDto`) a partir de la **sección** y el
  **epígrafe** (el título como respaldo), con reglas ordenadas y una categoría por
  defecto por sección. **Siempre** devuelve categoría (nunca `null`) y **conserva**
  el epígrafe original como metadato.
- `DeadlineExtractor`: extrae el **plazo de solicitud** de las convocatorias
  (`Publicacion.plazo`) de forma **heurística y acotada**: `plazo de <N> días
  [hábiles|naturales]` con la coletilla «a contar/contados/a partir … el día
  siguiente al de la publicación», y fechas explícitas («hasta el <d> de <mes> de
  <aaaa>» / «hasta el dd/mm/aaaa»). Si no es fiable devuelve `null` (**nunca
  inventa una fecha**); descarta plazos de ejecución, resolución, recurso,
  subsanación, alegaciones o informe. Los «días hábiles» cuentan solo sábados y
  domingos, **sin** calendario de festivos.

Ambas se cablean con colaboradores por defecto, así que el job diario y el
backfill las heredan sin cambios en el arranque. El comportamiento y los límites
quedan en `docs/adr/0007` (en el harness privado). No hay endpoint ni UI: la
clasificación se prueba con los **fixtures reales** de las ocho secciones en el
gate.

## Destacados del día (`:backend`)

El paquete `es.aviferdev.datopublico.backend.relevance` puntúa la lista de
publicaciones de un día y marca un **subconjunto** como destacado, explicando
**por qué** (`Highlight.score` + `Highlight.reasons`). Es un ranker **puro** (sin
IA, red, BD, reloj ni estado) y **determinista**: la salida no depende del orden
de entrada (orden por `score` descendente y, a igual puntuación, por `id`
ascendente, con tope) y un día sin señales devuelve lista vacía.

Señales objetivas y pesos (semilla determinista documentada):

- **Sección I** (normas): +2.
- **Rango normativo**: ley orgánica / ley / real decreto-ley / real decreto
  legislativo +4; real decreto +3; orden / orden ministerial / instrucción +2;
  resolución / acuerdo / circular +1; desconocido o ausente +0.
- **Categoría curada** (FT00012) de empleo público, becas/subvenciones o premios:
  +2.
- **Plazo de solicitud** fiable (`Publicacion.plazo`): +2.
- **Organismo emisor** en la lista curada de referencia (Jefatura del Estado,
  Presidencia del Gobierno, Cortes Generales, Consejo de Ministros, Presidencia):
  +1 (peso **modesto**; **nunca** decide por sí solo).

Es destacada la publicación con `score >= 4`, con un tope de 12 destacados por
día (constantes documentadas en `HighlightRanker`). **No se persiste**: se calcula
*on-the-fly*; el feed (FT00026) llamará a `PublicationRepository.listByDate(fecha)`
y pasará la lista a `HighlightRanker.rank(...)`. Decisión durable en el ADR 0008
(harness privado). No hay endpoint ni UI: se prueba solo en el gate, sin red ni
BD.

## Fragmentación por artículo o sección (`:backend`)

El paquete `es.aviferdev.datopublico.backend.rag` (modelo de dominio `Fragment`) y
su subpaquete `rag.chunking` (chunker puro `ArticleChunker`) dividen el texto de
una `Publicacion` en **fragmentos** listos para vectorizar (FT00015) y persistir
(FT00016+), sin red ni base de datos:

- **Por artículo/disposición** conservando la **referencia**: se detectan los
  encabezados `Artículo …`/`Disposición …` anclados a **inicio de línea** (el
  texto ya llega normalizado por el parser) y cada uno inicia un bloque con su
  encabezado y su cuerpo. Las líneas previas al primer encabezado forman el bloque
  **introductorio**.
- **Variante por sección**: las secciones sin artículos (II.A, II.B, IV, V.A, V.B y
  V.C) agrupan sus párrafos en fragmentos consecutivos con `reference` de sección
  (`"Sección " + seccion.name`, p. ej. `Sección II_B`).
- **Cobertura sin solapamiento** y `order` contiguo `0..n-1`: la concatenación de
  los fragmentos reproduce las líneas del texto de origen en orden, sin repetir ni
  omitir. Un párrafo que supere el presupuesto se parte por palabras conservando
  la misma `reference`.
- **Presupuesto del modelo E5 (≤512 tokens)**: la estimación es **provisional**
  (`ceil(caracteres / 3)`) porque el tokenizador real llega con FT00015; es
  conservadora y **inyectable** por el constructor de `ArticleChunker`
  (`maxTokens`, `charsPerToken`), que FT00015 recalibrará sin cambiar la firma.

El chunker es **puro y determinista** (sin IA, red, BD, reloj ni estado) y **no**
se cablea en el arranque. El encaje con la persistencia es la extensión pura
`Fragment.toEntity()` (`id = null`, sin abrir conexión), en `persistence/`.
`ArticleChunkerTest` cubre los escenarios con los **fixtures reales** de las ocho
secciones, en el gate y sin red ni BD. Decisión durable en el ADR 0009 (harness
privado).

## Embeddings locales (ONNX) (`:backend`)

El paquete `es.aviferdev.datopublico.backend.rag.embeddings` vectoriza texto con
`intfloat/multilingual-e5-small` **int8** vía **ONNX Runtime en CPU**, detrás de la
interfaz `EmbeddingProvider`. Aplica los prefijos obligatorios `query: ` (consulta)
y `passage: ` (fragmento), hace *mean pooling* con la máscara de atención,
**normaliza** los vectores (L2) y devuelve **384 dimensiones**; el tokenizador
XLM-R (DJL) expone `countTokens` (≤512 tokens). Es una **librería**: **no** se
cablea en el arranque ni en el job.

El modelo (`model.onnx`, ~118 MB) y el `tokenizer.json` **no** se versionan. Se
descargan una sola vez (con red, fuera del gate) a `backend/models/` (ignorado por
git):

```sh
backend/tools/download-embedding-model.sh
```

El proveedor los carga por **ruta absoluta** con **fail-fast** (si faltan, lanza
`EmbeddingException` con un mensaje claro; sin descarga silenciosa):

- `EMBEDDING_MODEL_PATH` — ruta del `.onnx` int8.
- `EMBEDDING_TOKENIZER_PATH` — ruta del `tokenizer.json`.
- `EMBEDDING_MAX_TOKENS` (opcional, por defecto `512`).

El gate (`./gradlew build`/`test`/`.harness/init.sh`) **no** descarga ni carga el
modelo: cubre el contrato y la aritmética con dobles deterministas. La prueba
contra el **modelo real** es **opt-in** y queda fuera del gate y de la CI:

```sh
EMBEDDING_MODEL_PATH="$PWD/backend/models/multilingual-e5-small/model.onnx" \
EMBEDDING_TOKENIZER_PATH="$PWD/backend/models/multilingual-e5-small/tokenizer.json" \
EMBEDDING_LIVE_TEST=1 ./gradlew :backend:test --tests '*E5EmbeddingLiveTest'
```

Decisión durable en el ADR 0010 (harness privado).

## Arranque de la base de datos

```sh
cp .env.example .env      # define una POSTGRES_PASSWORD local
docker compose up -d
```

## Migraciones Flyway

Tareas explícitas del módulo `:backend`, **fuera** de `build`/`check` (la CI corre
sin base de datos):

```sh
./gradlew :backend:flywayMigrate
./gradlew :backend:flywayInfo
```

## Persistencia (repositorios JDBC)

La capa de datos de `:backend` vive en
`es.aviferdev.datopublico.backend.persistence` (repositorios **JDBC** +
entidades `...Entity`) con la configuración de conexión
(`infra/DatabaseConfig`, derivada de `POSTGRES_*`) y el pool **HikariCP**
(`infra/Database`, tamaño ≤5). Desde FT00026 el composition root (`Application.kt`)
crea **un único** pool cuando la configuración de BD resuelve, lo comparte entre la
API (feed, detalle y búsqueda) y la ingesta y lo cierra **una sola vez** en
`ApplicationStopped`; sin BD no se crea pool y ni el feed, ni el detalle ni la
búsqueda se registran.

La migración `V2__persistencia_publicaciones.sql` crea las tablas `publicacion`,
`fragmento`, `resumen` y `cola_revision`; la `V3__fragmento_embedding.sql` añade
la columna `embedding vector(384)` (nullable) y su índice HNSW coseno (FT00016);
la `V4__resumen_cache_uso.sql` crea `resumen_cache` y `generacion_uso` (FT00021,
caché y uso de tokens); y los valores de `categoria`/`plazo` los produce la ingesta
desde FT00012 (columnas nullable). El driver PostgreSQL y HikariCP están en el
classpath de producción de `:backend`.

Los tests del gate cubren la configuración de conexión, el mapeo
dominio↔entidad y el contrato SQL de la migración **sin** base de datos. La
prueba real (guardar/recuperar con categoría/sección/epígrafe/plazo, *upsert*
idempotente, nulos conservados y FK `ON DELETE CASCADE`) es **opt-in** y queda
fuera de `build`/`test`/`init.sh` y de la CI:

```sh
cp .env.example .env
docker compose up -d
./gradlew :backend:flywayMigrate
DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*PublicationPersistenceLiveTest'
docker compose down -v
```

El test opt-in lee `POSTGRES_*` del entorno y, si faltan, de la `.env` de la raíz
(no hace falta exportarlas).

## Indexado vectorial (pgvector) (`:backend`)

La migración `V3__fragmento_embedding.sql` (FT00016) añade a `fragmento` la
columna `embedding vector(384)` **nullable** y su índice **HNSW** con distancia
coseno (`vector_cosine_ops`, `m = 16`, `ef_construction = 64`). La conversión
entre `FloatArray` y el literal `[v1,v2,…]` de pgvector vive en el objeto **puro**
`es.aviferdev.datopublico.backend.persistence.PgVector` (dimensión 384, fail-fast,
formato independiente del *locale*) y `FragmentRepository` gana `saveEmbedding`
(actualiza el vector de un fragmento existente) y `findNearest` (los `k` fragmentos
más cercanos por distancia coseno, excluyendo los que no tienen embedding). El
vector se enlaza como **texto con *cast* `?::vector`**, sin ORM ni `PGobject`. No
hay filtros de metadatos (recuperación híbrida → FT00017) ni wiring en el arranque:
es una librería.

El gate cubre la conversión pura y el contrato SQL de la migración **sin** base de
datos (`PgVectorTest`, `FragmentEmbeddingMigrationSqlTest`). La prueba real
(similitud, exclusión de nulos e índice HNSW usado) es **opt-in** y queda fuera de
`build`/`test`/`init.sh` y de la CI:

```sh
cp .env.example .env
docker compose up -d
./gradlew :backend:flywayMigrate
DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*FragmentEmbeddingPersistenceLiveTest'
docker compose down -v
```

Decisión durable en el ADR 0011 (harness privado).

## Recuperación híbrida e indexado (`:backend`)

El paquete `es.aviferdev.datopublico.backend.rag.retrieval` (FT00017) recupera
fragmentos combinando **filtros SQL por metadatos** con la **similitud vectorial**:

- `SearchFilter` es el filtro interno (fechas ISO-8601 `publishedFrom`/`publishedTo`
  inclusivas, `category`, `section` y `organization`; todos opcionales).
- `SearchFilterSql` traduce el filtro a una cláusula ` AND …` parametrizada y a sus
  parámetros (orden documentado; las fechas se enlazan como texto con *cast*
  `?::date`; una fecha no ISO falla con `IllegalArgumentException`).
- `HybridSearch.search(query, filter, limit)` vectoriza la consulta con
  `EmbeddingProvider.embedQuery` (prefijo `query: `) y llama a
  `FragmentRepository.findNearest(vector, filter, limit)` (con `JOIN publicacion` y
  orden por distancia coseno ascendente, excluyendo fragmentos sin embedding).

El paquete `rag.indexing` cierra el aplazamiento de FT00016 con un **indexado
mínimo**: `FragmentIndexer.index(publicacion)` fragmenta (`ArticleChunker`),
persiste cada fragmento (`save`) y guarda su embedding de *pasaje*
(`embedPassage` + `saveEmbedding`). Es **idempotente** por el *upsert* de
`(publicacion_id, orden)`. **No** indexa el corpus completo, no hace *batching* y
**no** se cablea en el arranque: es una librería.

El gate cubre la traducción SQL, el servicio y el indexado **sin** red ni BD
(`SearchFilterSqlTest`, `HybridSearchTest`, `FragmentIndexerTest`). Las pruebas
reales son **opt-in** y quedan fuera de `build`/`test`/`init.sh` y de la CI. La
primera (filtros sobre PostgreSQL con vectores sintéticos) solo necesita la base
de datos:

```sh
cp .env.example .env
docker compose up -d
./gradlew :backend:flywayMigrate
DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*HybridSearchLiveTest'
```

La segunda es el extremo a extremo con el **modelo E5 real** y PostgreSQL (indexa
la publicación y recupera el fragmento relevante primero, aplicando el filtro):

```sh
backend/tools/download-embedding-model.sh
DB_LIVE_TEST=1 EMBEDDING_LIVE_TEST=1 ./gradlew :backend:test --tests '*FragmentIndexerLiveTest'
docker compose down -v
```

Decisión durable en el ADR 0012 (harness privado).

## Evaluación de recuperación (`:backend:evaluateRetrieval`)

El paquete `es.aviferdev.datopublico.backend.rag.evaluation` (FT00018) mide la
calidad de la recuperación sobre un **golden set versionado**
(`backend/src/main/resources/rag/eval/golden-set.json`): un corpus modesto con
encabezados `Artículo N` explícitos (para que el `order` del chunker sea
determinista) y consultas con su `filter` y sus fragmentos esperados.

- `RetrievalMetrics` es **puro**: `precisionAtK`, `recallAtK` y `reciprocalRank`
  (MRR) con sus casos límite.
- `RetrievalGoldenSetLoader` parsea y **valida** el dataset (fail-fast con
  `IllegalArgumentException`: `k >= 1`, corpus/consultas no vacíos, ids únicos y
  cada fragmento relevante existente en el corpus).
- `RetrievalEvaluator` recupera una vez por consulta con un `Retriever`
  inyectable y agrega las medias sin `NaN`.
- La CLI `RetrievalEvaluationMain` indexa el corpus del propio dataset con el
  modelo E5 real (`FragmentIndexer`, idempotente), evalúa cada consulta con
  `HybridSearch` y registra el resultado (log JSON
  `event=retrieval.evaluation.completed` con `precision`/`recall`/`mrr` + resumen
  por consulta en stdout). **No** se cablea en el arranque.

El gate cubre las métricas, el dataset y el evaluador **sin red, BD ni modelo**
(`RetrievalMetricsTest`, `RetrievalGoldenSetTest`, `RetrievalEvaluatorTest`). La
ejecución real es **opt-in** y queda fuera de `build`/`test`/`init.sh` y de la CI:

```sh
backend/tools/download-embedding-model.sh
cp .env.example .env && docker compose up -d
./gradlew :backend:flywayMigrate
# La CLI lee el entorno del proceso (no la .env): exporta la conexión.
set -a; . ./.env; set +a   # o exporta POSTGRES_* a mano
DB_LIVE_TEST=1 EMBEDDING_LIVE_TEST=1 ./gradlew :backend:evaluateRetrieval
DB_LIVE_TEST=1 EMBEDDING_LIVE_TEST=1 ./gradlew :backend:test --tests '*RetrievalEvaluationLiveTest'
docker compose down -v
```

Variables de entorno (opcionales; la CLI hereda el entorno del proceso):

- `POSTGRES_*`: conexión a la base. La CLI (`RetrievalEvaluationMain`) usa
  `DatabaseConfig.fromEnv()`, es decir el **entorno del proceso**: `POSTGRES_PASSWORD`
  debe estar **exportada** (la CLI no lee la `.env` de la raíz, a diferencia de
  Flyway y de los tests opt-in). Carga la `.env` con `set -a; . ./.env; set +a` o
  exporta las variables a mano.
- `RETRIEVAL_EVAL_DATASET`: fichero alternativo del golden set (por defecto, el
  recurso versionado).
- `RETRIEVAL_EVAL_K`: override de la ventana `k` (entero `>= 1`).
- `EMBEDDING_MODEL_PATH`/`EMBEDDING_TOKENIZER_PATH`: rutas del modelo (o el
  directorio por defecto `backend/models/multilingual-e5-small/`).

Decisión durable en el ADR 0013 (harness privado).

## Evaluación de resúmenes (`:backend:evaluateSummary`)

El paquete `es.aviferdev.datopublico.backend.rag.evaluation` (FT00024) mide la
calidad de los **resúmenes ciudadanos** sobre un **golden set versionado**
(`backend/src/main/resources/rag/eval/summary-golden-set.json`): un corpus modesto
de publicaciones (con encabezados `Artículo N` para que el `order` del chunker sea
determinista e ids con prefijo `EVAL-S`) y, en cada entrada, su **resumen de
referencia** (`queCambia`, `aQuienAfecta`, `cifrasClave` y `plazo`).

- `SummaryQualityMetrics` es **puro**: `tokenF1` (F1 léxico por campo con el
  multiconjunto de términos de `SummaryTextNormalizer`) y `figuresRecall` (fracción
  de cifras de la referencia presentes en el resumen), con sus casos límite.
- `SummaryGoldenSetLoader` parsea y **valida** el dataset (fail-fast con
  `IllegalArgumentException`: versión y entradas no vacías, ids únicos, categoría,
  título y texto presentes y referencia con «¿Qué cambia?» y «¿A quién afecta?»).
- `SummaryQualityEvaluator` genera **una vez por entrada** con un `SummaryGenerator`
  inyectable, calcula el F1 de cada campo y el *recall* de cifras, deriva el acierto
  (`hit`), agrega las medias sin `NaN` y cuenta los resúmenes inválidos como fallo.
- La CLI `SummaryEvaluationMain` indexa el corpus del propio dataset con el modelo
  E5 real (`FragmentIndexer`, idempotente), genera y evalúa cada publicación con
  `CitizenSummaryGenerator` (OpenCode) y registra el resultado (log JSON
  `event=summary.evaluation.completed` con `what_changes_f1`/`who_is_affected_f1`/
  `figures_recall`/`hit_rate`/`invalid` + resumen por entrada en stdout). **No** se
  cablea en el arranque.

El gate cubre las métricas, el dataset y el evaluador **sin red, BD, modelo ni
OpenCode** (`SummaryQualityMetricsTest`, `SummaryGoldenSetTest`,
`SummaryQualityEvaluatorTest`). La ejecución real es **opt-in** y queda fuera de
`build`/`test`/`init.sh` y de la CI:

```sh
backend/tools/download-embedding-model.sh
cp .env.example .env && docker compose up -d
./gradlew :backend:flywayMigrate
# La CLI lee el entorno del proceso (no la .env): exporta la conexión.
set -a; . ./.env; set +a   # o exporta POSTGRES_* a mano
OPENCODE_API_KEY=<clave> OPENCODE_LIVE_TEST=1 DB_LIVE_TEST=1 EMBEDDING_LIVE_TEST=1 \
  ./gradlew :backend:evaluateSummary
OPENCODE_API_KEY=<clave> OPENCODE_LIVE_TEST=1 DB_LIVE_TEST=1 EMBEDDING_LIVE_TEST=1 \
  ./gradlew :backend:test --tests '*SummaryEvaluationLiveTest'
docker compose down -v
```

Variables de entorno (opcionales; la CLI hereda el entorno del proceso):

- `POSTGRES_*`: conexión a la base. La CLI (`SummaryEvaluationMain`) usa
  `DatabaseConfig.fromEnv()`, es decir el **entorno del proceso**: `POSTGRES_PASSWORD`
  debe estar **exportada** (la CLI no lee la `.env` de la raíz, a diferencia de
  Flyway y de los tests opt-in).
- `OPENCODE_API_KEY` (obligatoria, fail-fast) y `OPENCODE_BASE_URL`/`OPENCODE_MODEL`/
  `OPENCODE_TEMPERATURE` (opcionales): configuración del proveedor de generación.
- `SUMMARY_EVAL_DATASET`: fichero alternativo del golden set (por defecto, el
  recurso versionado).
- `EMBEDDING_MODEL_PATH`/`EMBEDDING_TOKENIZER_PATH`: rutas del modelo (o el
  directorio por defecto `backend/models/multilingual-e5-small/`).

Decisión durable en el ADR 0019 (harness privado).

## Generación del resumen ciudadano (`:backend`)

Los paquetes `es.aviferdev.datopublico.backend.rag.generation` y
`...backend.rag.summary` (FT00020) generan el **resumen ciudadano** de una
publicación a partir de los fragmentos recuperados, sin cablearse en el arranque:

- `TextGenerationProvider` es la interfaz **intercambiable** de generación de
  texto (`suspend`); `OpenCodeTextGenerationProvider` la implementa contra la API
  OpenAI-compatible de OpenCode (`POST {baseUrl}/chat/completions` con
  `Authorization: Bearer`), con DTOs de wire y `openCodeHttpClient` (CIO +
  `ContentNegotiation` + `HttpTimeout`).
- `CitizenSummaryPromptBuilder` (puro) construye el prompt: metadatos de la
  publicación, contexto delimitado como **datos, no instrucciones** y esquema JSON;
  la variante de empleo/beca pide el **plazo**.
- `SummaryOutputParser` (puro) quita las *fences* de Markdown y parsea el JSON
  (`SummaryGenerationException` si está vacío o es inválido).
- `CitizenSummaryGenerator` compone `HybridSearch` → prompt → proveedor → parser →
  **inyecta la fuente oficial** desde `Publicacion.urlOficial` → valida el contrato
  con `CitizenSummaryValidator` y devuelve `CitizenSummaryResult.Valid`/`Invalid`
  (un resumen inválido **no** se publica).

La **clave** vive solo en el servidor y es **fail-fast** si falta:

- `OPENCODE_API_KEY` — clave de OpenCode (**obligatoria**; nunca se versiona).
- `OPENCODE_BASE_URL` (por defecto `https://opencode.ai/zen/go/v1`).
- `OPENCODE_MODEL` (por defecto `deepseek-v4.1-flash`).
- `OPENCODE_TEMPERATURE` (por defecto `0.0`).

El gate cubre config, proveedor (`MockEngine`), parser, prompt y generador con
**dobles deterministas**, sin red ni BD. La prueba contra el endpoint **real** es
**opt-in**, no requiere PostgreSQL ni el modelo E5 y queda fuera del gate y de la
CI (con `OPENCODE_LIVE_TEST=1` pero sin clave, falla en claro):

```sh
OPENCODE_LIVE_TEST=1 OPENCODE_API_KEY=<clave> \
  ./gradlew :backend:test --tests '*CitizenSummaryGeneratorLiveTest'
```

Decisión durable en el ADR 0015 (harness privado).

## Chat RAG (`:backend`)

El paquete `es.aviferdev.datopublico.backend.rag.chat` (FT00025) genera la
**respuesta del chat RAG**: dada una pregunta o una afirmación, recupera los
fragmentos, construye el prompt, invoca el **proveedor de texto** (OpenCode en
producción; un doble en los tests), parsea una salida estructurada
(**estado + respuesta + citas**) y devuelve un resultado tipado con los tres
estados del dominio. Es una **librería sin wiring**: no se cablea en el arranque
(el endpoint, el rate limiting y la UI son FT00031/FT00037).

- `ChatStatus` (`ANSWERED`/`REFUTED`/`UNFOUNDED`) y `ChatResult`
  (`Answered`/`Refuted`/`Unfounded`) modelan los tres estados del dominio;
  `ChatCitation` lleva la fuente oficial y `ChatGenerationException` es el error
  tipado.
- `ChatPromptBuilder` (puro) construye el prompt con la consulta y el contexto
  **numerado** `[0]`, `[1]`… delimitado como **datos, no instrucciones**; el
  esquema pide `estado`, `respuesta` y `citas` (índices de fragmento).
- `ChatOutputParser` (puro) quita las *fences* de Markdown, parsea el JSON
  (`ChatGenerationException` si está vacío o es inválido) y traduce el token de
  estado con **fail-fast** si es desconocido.
- `ChatGroundingChecker` (puro) reutiliza `SummaryTextNormalizer` y
  `SummaryGroundingChecker.MIN_CLAIM_SUPPORT` (= 0.6): una respuesta está
  sustentada si **todas** sus oraciones lo están (todas sus cifras en el contexto y
  ≥ 0.6 de sus términos de contenido).
- `CitationResolver` es el `fun interface` que **inyecta** la fuente oficial desde
  la publicación (en producción, `PublicationRepository.findById`); el modelo solo
  aporta **índices** de fragmento, **nunca** URLs.
- `RagChatService.answer(query, filter, limit)`: recupera con `HybridSearch`; con
  **contexto vacío** declara `sin_fundamento` **sin invocar** al proveedor; una
  respuesta `respondida`/`refutada` no sustentada por el grounding **degrada** a
  `sin_fundamento`; y exige **al menos una cita resoluble** (índice fuera de rango
  o estado desconocido ⇒ `ChatGenerationException`).

El gate cubre prompt, parser, grounding y servicio con **dobles deterministas**,
sin red ni BD. La prueba contra el endpoint **real** es **opt-in**, no requiere
PostgreSQL ni el modelo E5 y queda fuera del gate y de la CI (con
`OPENCODE_LIVE_TEST=1` pero sin clave, falla en claro):

```sh
OPENCODE_LIVE_TEST=1 OPENCODE_API_KEY=<clave> \
  ./gradlew :backend:test --tests '*RagChatServiceLiveTest'
```

Decisión durable en el ADR 0020 (harness privado).

## Cacheo del resumen y control de coste (`:backend`)

El paquete `es.aviferdev.datopublico.backend.rag.summary` añade (FT00021) el
**cacheo del resumen ciudadano** y el **registro del consumo de tokens**, como
**librería sin wiring**:

- `SummaryCacheKey.of(publicationId, model, promptVersion)` es la clave **pura y
  determinista** (`"$publicationId|$model|$promptVersion"`): incluye **modelo** y
  **versión del prompt** para que cambiar cualquiera de los dos **invalide** la
  caché (nunca sirve un resumen obsoleto). Falla con `IllegalArgumentException` si
  algún componente está en blanco.
- `CitizenSummaryPromptBuilder.PROMPT_VERSION` (`"v1"`) es la versión del prompt,
  **manual**: **súbela** al cambiar el prompt de forma incompatible.
- `CachedCitizenSummaryGenerator` **envuelve** a `CitizenSummaryGenerator` (no
  reescribe su pipeline): consulta la caché **antes** del proveedor. En un
  **acierto** devuelve el resumen cacheado **sin llamar** a OpenCode y registra un
  uso con `cache_hit = true` y 0 tokens; en un **fallo** genera, registra el uso
  real y **cachea solo si el resumen es `Valid`**.
- El **modelo** se inyecta por constructor (en producción, de `OpenCodeConfig`); la
  caché **no** lo lee de la config.
- `GenerationResponse` expone `usage: TokenUsage?` (opcional: sin bloque `usage` es
  `null`, no un error) y `CitizenSummaryResult` propaga el uso de forma compatible.

La persistencia son dos repositorios JDBC en `persistence/`
(`SummaryCacheRepository` con codec JSON puro del `ResumenDto` y
`GenerationUsageRepository` con `totalTokens()`). La migración
`V4__resumen_cache_uso.sql` crea `resumen_cache` (caché del resumen por
`cache_key`, **tabla propia y separada** de `resumen`, con FK en cascada a
`publicacion` y *upsert* idempotente) y `generacion_uso` (log de tokens, **sin**
FK). Se registran **tokens**, no tarifas.

El gate cubre la clave, el servicio con caché, el uso del proveedor y el contrato
SQL de `V4` con **dobles deterministas**, sin red ni BD. La prueba real es
**opt-in** (solo PostgreSQL, sin OpenCode ni modelo E5) y queda fuera de
`build`/`test`/`init.sh` y de la CI:

```sh
cp .env.example .env
docker compose up -d
./gradlew :backend:flywayMigrate
DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*SummaryCachePersistenceLiveTest'
docker compose down -v
```

Esperado: `find` devuelve el mismo `ResumenDto`; el *upsert* de la caché es
idempotente por `cache_key`; `totalTokens()` cuadra con los registros. Decisión
durable en el ADR 0016 (harness privado).

## Guardrails anti-alucinación y trazabilidad (`:backend`)

El paquete `es.aviferdev.datopublico.backend.rag.guardrails` (FT00022) verifica,
**después** de generar el resumen y **antes** de publicarlo, que la **fuente
oficial** es válida y que las **afirmaciones** están sustentadas por el contexto
recuperado. Es una **librería sin wiring**: no se cablea en el arranque y **no**
persiste nada (ni `resumen` ni `cola_revision`).

- `SummaryGuardrail.evaluate(publication, summary, context)` combina la validación
  de **contrato** (`CitizenSummaryValidator`, FT00019), la de **fuente**
  (`OfficialSourceValidator`: `https://`, host del BOE e **igualdad** con
  `Publicacion.urlOficial`) y el **grounding** léxico
  (`SummaryTextNormalizer` + `SummaryGroundingChecker`). Devuelve un
  `SummaryGuardrailResult` con `GuardrailDecision` (`PUBLISHABLE`/`NEEDS_REVIEW`/
  `DISCARDED`), `isPublishable`, las violaciones de contrato y de contenido y una
  **puntuación de grounding** `[0,1]`.
- **Grounding**: una afirmación está sustentada si **todas** sus cifras están en el
  contexto y ≥ `MIN_CLAIM_SUPPORT` (= 0.6) de sus términos de contenido (sin
  acentos, sin palabras vacías) aparecen en el contexto; `score` = sustentadas /
  total. Umbrales `DISCARD_THRESHOLD` (= 0.5) y `REVIEW_THRESHOLD` (= 0.8). Es
  **conservador**: un falso negativo lleva a revisión o descarte, nunca a publicar.
- **Alcanzabilidad**: puerto `suspend` `SourceReachability`; `HttpSourceReachability`
  lo implementa con un `HttpClient` inyectado (2xx/3xx ⇒ alcanzable; fallo de red ⇒
  no alcanzable). El guardrail lo recibe **opcional**: sin puerto solo se valida la
  **estructura** de la fuente.

El gate cubre el normalizador, el validador de fuente, el grounding, el evaluador
con **dobles deterministas** y `HttpSourceReachability` con `MockEngine`, **sin red
ni BD**. La alcanzabilidad real es **opt-in** y queda fuera de
`build`/`test`/`init.sh` y de la CI:

```sh
BOE_LIVE_TEST=1 ./gradlew :backend:test --tests '*SummaryGuardrailLiveTest'
```

Nota (macOS con el JBR de Android Studio): como en el resto de pruebas opt-in del
BOE, si el truststore del JVM no incluye la raíz **FNMT-RCM**, el test falla con
`SunCertPathBuilderException`; `curl` sí valida porque usa el llavero del sistema.
El gate no se ve afectado. Decisión durable en el ADR 0017 (harness privado).

## Cola de revisión y disposición del resumen (`:backend`)

El paquete `es.aviferdev.datopublico.backend.rag.review` (FT00023) **dispone** el
resumen ciudadano **después** de los guardrails (FT00022): persiste su **estado** y
su **confianza** (`groundingScore`) en `resumen` y, si la decisión es
`NEEDS_REVIEW`, lo encola en `cola_revision` **sin publicarlo**. Es una **librería
sin wiring**: compone repositorios inyectados y **no** se cablea en el arranque.

- `SummaryState` es el enum de los **siete** estados del resumen
  (`pendiente`, `generado`, `validado_por_guardrails`, `publicado`,
  `en_cola_revision`, `revisado`, `descartado`); el token persistido coincide con
  `resumen.estado`/`cola_revision.estado`, así que no requiere migración de datos.
- `SummaryReviewOutcome` es el resultado tipado (`Published`/`Queued`/`Discarded`)
  con los `id` persistidos.
- `ReviewReasonFormatter.of(result)` construye el `motivo` de la cola (nombres de
  las violaciones de contenido, o `LOW_GROUNDING`); es diagnóstico interno, no
  texto de UI.
- `SummaryDispositionService.dispose(publication, summary, guardrail)` **consume**
  la `GuardrailDecision` de FT00022 (sin umbral nuevo): `PUBLISHABLE` publica
  (`estado = publicado`), `NEEDS_REVIEW` encola (`en_cola_revision` + entrada
  `pendiente`) y `DISCARDED` queda `descartado`. La `confianza` persistida es el
  `groundingScore` y el resumen publicado lleva `aviso_ia = true` (el aviso
  **visible** es FT00033).
- El **encolado es idempotente**: la migración `V5__cola_revision_pendiente_unique.sql`
  crea un **índice único parcial** (`resumen_id WHERE estado = 'pendiente'`) y el
  `ReviewQueueRepository` hace *upsert* (`ON CONFLICT … DO UPDATE SET motivo`)
  más `listPending()`. Reencolar un resumen ya pendiente **actualiza** el motivo sin
  duplicar la fila.

El gate cubre los estados, el motivo, el servicio con **dobles deterministas** y el
contrato SQL de `V5`, **sin red ni BD**. La prueba real (publicar + encolar +
reencolado idempotente) es **opt-in** y queda fuera de `build`/`test`/`init.sh` y
de la CI (solo PostgreSQL, sin OpenCode ni modelo E5):

```sh
cp .env.example .env
docker compose up -d
./gradlew :backend:flywayMigrate
DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*ReviewQueuePersistenceLiveTest'
docker compose down -v
```

Esperado: el publicado se lee `publicado` con su `confianza`; el encolado aparece
en `listPending()`; reencolarlo no duplica la fila pendiente. Decisión durable en
el ADR 0018 (harness privado).

## Feed diario (`GET /feed`) (`:backend`)

El paquete `es.aviferdev.datopublico.backend.feed` (FT00026) expone el **primer
endpoint de lectura de datos** del backend: el **feed diario** paginado. Es la
primera feature que comparte un **pool JDBC único** entre la API y la ingesta: sin
base de datos (`POSTGRES_PASSWORD` ausente) el pool no se crea, `/feed` **no** se
registra (404) y `/health` sigue operativo.

```
GET /feed?fecha=2026-10-11&pagina=1&tamanoPagina=20
```

- `fecha` (**obligatoria**, ISO `YYYY-MM-DD`) selecciona el día; el feed **no**
  filtra por categoría (eso es FT00028).
- `pagina` (default `1`) y `tamanoPagina` (default `20`, máx `100`) paginan.
- Responde `200` con `fecha`, `publicaciones` (solo las que tienen un resumen
  `estado = 'publicado'`, cada una con su `resumen`, `categoria`, `urlOficial` y
  `plazo`), `destacados` (calculados *on-the-fly* con `HighlightRanker`, con sus
  `motivos` y **sin** `score`) y `total`/`totalPaginas`.
- Un día sin publicado responde `200` con listas vacías; `fecha` ausente/no ISO o
  `pagina`/`tamanoPagina` no entero, `< 1` o `> 100` responden `400` (sin cuerpo
  tipado; el contrato de error es FT00030).
- La lectura JDBC bloqueante se ejecuta en `Dispatchers.IO`; el SQL está
  parametrizado y filtra `estado = 'publicado'`.

```sh
curl -s 'http://localhost:8080/feed?fecha=2026-10-11' | jq .
```

La prueba real (solo el resumen publicado se sirve) es **opt-in** y queda fuera del
gate y de la CI:

```sh
cp .env.example .env
docker compose up -d
./gradlew :backend:flywayMigrate
DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*FeedPersistenceLiveTest'
docker compose down -v
```

Decisión durable en el ADR 0021 (harness privado).

## Detalle de publicación (`GET /publicacion/{id}`) (`:backend`)

El paquete `es.aviferdev.datopublico.backend.publication` (FT00027) expone el
**segundo endpoint de lectura de datos**: el detalle de una publicación por su
identificador oficial del BOE. Reutiliza el contrato `PublicacionDto` de `:shared`
y el mapeo puro `feed/FeedDtoMapper` de FT00026 (no hay DTO nuevo) y comparte el
**mismo pool JDBC único** que el feed y la ingesta.

```
GET /publicacion/BOE-A-2026-20979
```

- Sirve **solo** publicaciones con `resumen.estado = 'publicado'` (token
  `SummaryState.PUBLISHED` que escribe FT00023); un resumen no publicado o ausente,
  un identificador desconocido y una publicación sin categoría (dato corrupto)
  responden `404`.
- Responde `200` con el `PublicacionDto` completo (`id`, `titulo`,
  `fechaPublicacion`, `organismo`, `seccion`, `epigrafe`, `categoria`, `urlOficial`,
  `resumen` con `queCambia`/`aQuienAfecta`/`cifrasClave`/`fuenteOficial`/`avisoIA` y
  `plazo` si la publicación lo tiene).
- Un `id` **en blanco** o más largo que `PublicationDetailService.MAX_ID_LENGTH`
  responde `400` (sin cuerpo tipado; el contrato de error es FT00030).
- La lectura JDBC bloqueante se ejecuta en `Dispatchers.IO`; el detalle compone
  `PublicationRepository.findById` + `SummaryRepository.findByPublication` (dos
  *lookups* por clave, sin `JOIN` nuevo) y **no** duplica el SQL del feed.
- Sin base de datos el pool no se crea, la ruta **no** se registra (404) y
  `/health` sigue operativo.

```sh
curl -s 'http://localhost:8080/publicacion/BOE-A-2026-20979' | jq .
```

La prueba real (solo el resumen publicado se sirve) es **opt-in** y queda fuera del
gate y de la CI:

```sh
cp .env.example .env
docker compose up -d
./gradlew :backend:flywayMigrate
DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*PublicationDetailLiveTest'
docker compose down -v
```

Decisión durable en el ADR 0022 (harness privado).

## Búsqueda (`GET /buscar`) (`:backend`)

El paquete `es.aviferdev.datopublico.backend.search` (FT00028) expone la
**búsqueda** sobre el corpus: dada una consulta, devuelve las publicaciones
**publicadas** más relevantes por **similitud semántica** sobre los fragmentos
indexados. Reutiliza la recuperación híbrida (`rag/retrieval`, FT00017) y el mapeo
`feed/FeedDtoMapper` (FT00026), y solo sirve publicaciones con resumen
`estado = 'publicado'` (coherente con `/feed` y `/publicacion/{id}`).

```
GET /buscar?q=<consulta>&categoria=<token>&desde=<ISO>&hasta=<ISO>&limite=<n>
```

- `q` (**obligatoria**, no vacía) es la consulta.
- `categoria` (opcional) es el **token de wire** de `CategoriaDto`
  (p. ej. `becas_subvenciones_y_ayudas`), no el nombre del enum.
- `desde`/`hasta` (opcionales, ISO `YYYY-MM-DD`) acotan por fecha de publicación
  de forma **inclusiva**.
- `limite` (opcional, default `10`, máx `50`) acota el número de publicaciones.
- Responde `200` con `q` (eco de la consulta) y `resultados` (cada uno el mismo
  `PublicacionDto` del feed, con su `resumen`, `categoria`, `urlOficial` y
  `plazo`), **deduplicados** por publicación y ordenados por relevancia. Una
  consulta sin resultados y un rango `desde > hasta` responden `200` con lista
  vacía; un parámetro inválido responde `400` sin cuerpo tipado (el contrato de
  error es FT00030).
- La lectura JDBC y la inferencia ONNX (bloqueantes) se ejecutan en
  `Dispatchers.IO`; el SQL está parametrizado.

```sh
curl -s 'http://localhost:8080/buscar?q=becas%20para%20j%C3%B3venes&categoria=becas_subvenciones_y_ayudas&limite=5' | jq .
```

La búsqueda es el **primer** cableado del proveedor de embeddings: **sin** el
modelo E5 (o sin config de BD) el servidor arranca igual, `/buscar` **no** se
registra (`404`) y `/health` sigue operativo. El modelo se aprovisiona como se
describe en «Embeddings locales (ONNX)» (véase esa sección).

La prueba real (extremo a extremo con el **modelo E5** y PostgreSQL: la publicación
relevante va primero y los filtros se respetan) es **opt-in** y queda fuera del gate
y de la CI:

```sh
cp .env.example .env
docker compose up -d
./gradlew :backend:flywayMigrate
backend/tools/download-embedding-model.sh
DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*SearchPersistenceLiveTest'
DB_LIVE_TEST=1 EMBEDDING_LIVE_TEST=1 ./gradlew :backend:test --tests '*SearchServiceLiveTest'
docker compose down -v
```

Decisión durable en el ADR 0023 (harness privado).

## Chat RAG (`POST /chat`) (`:backend`)

El paquete `es.aviferdev.datopublico.backend.chat` (FT00031) expone el **endpoint
de chat RAG**: dada una **pregunta** o una **afirmación a verificar** en lenguaje
natural, devuelve una **respuesta fundamentada** con los tres estados del dominio y
las **citas a la fuente oficial**. Cablea por primera vez la librería `rag/chat`
(FT00025), su primer `CitationResolver` de producción
(`PublicationCitationResolver`) y el proveedor de texto de OpenCode sobre el
**mismo pool JDBC único** que el feed/el detalle/la ingesta.

```
POST /chat
Content-Type: application/json

{"consulta":"¿Cuál es el plazo de solicitud?"}
```

- Responde `200` con `ChatResponseDto`: `consulta` (eco), `estado`
  (`respondida`/`refutada`/`sin_fundamento`), `respuesta` y `citas` (cada una con
  `publicacionId`, `titulo` y `urlOficial`).
- `sin_fundamento` **no** es un error (`200`) y lleva `citas` vacía; el proveedor
  **no** se invoca si no hay contexto recuperado. `respondida`/`refutada` llevan
  **≥1** cita; la URL oficial la **inyecta** el servidor desde la publicación (el
  modelo solo aporta índices de fragmento).
- Una `consulta` ausente, en blanco, más larga que
  `ChatService.MAX_QUERY_LENGTH` o un cuerpo ilegible responden `400` (sin cuerpo
  tipado; el contrato de error es FT00030).
- Un fallo del proveedor de OpenCode o una salida no interpretable responden `502`;
  un fallo de la inferencia local (ONNX) responde `500`.
- El contexto se acota al `limit` por defecto de `RagChatService` (no controlable
  por el cliente); **no** hay historial ni caché y el consumo de tokens (`usage`)
  no se expone. El rate limiting es FT00030.
- La recuperación ONNX/JDBC y la resolución de citas JDBC se ejecutan en
  `Dispatchers.IO`; la clave de OpenCode vive **solo** en el servidor.

```sh
curl -s -X POST http://localhost:8080/chat \
  -H 'Content-Type: application/json' \
  -d '{"consulta":"¿Cuál es el plazo de solicitud?"}' | jq .
```

Sin base de datos, sin el modelo E5 o sin `OPENCODE_API_KEY`, `/chat` **no** se
registra (404) y `/health` sigue operativo. El arranque real necesita las tres
cosas: `docker compose up -d` + `./gradlew :backend:flywayMigrate`, el modelo
(`backend/tools/download-embedding-model.sh`) y la clave (`OPENCODE_API_KEY`). La
prueba real contra OpenCode (respuesta con cita) es **opt-in** y queda fuera del
gate y de la CI:

```sh
OPENCODE_LIVE_TEST=1 OPENCODE_API_KEY=<clave> \
  ./gradlew :backend:test --tests '*ChatRouteLiveTest'
```

La resolución real de la cita contra PostgreSQL es **opt-in**:

```sh
cp .env.example .env
docker compose up -d
./gradlew :backend:flywayMigrate
DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*PublicationCitationResolverLiveTest'
docker compose down -v
```

Decisión durable en el ADR 0024 (harness privado).

## Ingesta diaria (job programado)

El paquete `es.aviferdev.datopublico.backend.ingesta.job` programa la ingesta
completa (**descarga del sumario → parseo del texto XML → persistencia**) a las
**09:30** con una **segunda pasada a las 18:00** (`Europe/Madrid` por defecto).
Es un **scheduler interno por corrutina** (sin cron externo; ADR 0004), con el
reloj y la espera inyectables, así que el disparo se prueba con tiempo virtual,
sin esperar en real.

Solo se arranca **si la configuración de BD resuelve** (`POSTGRES_PASSWORD`
presente). Sin BD, el servidor arranca igual y el job se omite; con BD, el log
muestra `Ingesta programada: horas=[09:30, 18:00] zona=Europe/Madrid
gracia=30min`. El job **asume el esquema aplicado**
(`./gradlew :backend:flywayMigrate`) y se cierra (pool y clientes HTTP) al parar
el servidor.

Variables de entorno (todas opcionales; un valor inválido aborta el arranque):

- `INGESTA_ENABLED` (por defecto `true`): si es `false`, no se programa el job.
- `INGESTA_TIMEZONE` (por defecto `Europe/Madrid`).
- `INGESTA_PRIMARY_TIME` (por defecto `09:30`).
- `INGESTA_SECONDARY_TIME` (por defecto `18:00`).
- `INGESTA_GRACE_MINUTES` (por defecto `30`): margen entero de minutos > 0 que se
  espera tras una ventana antes de considerarla perdida.

### Observabilidad y alertas

Cada ejecución del job deja una línea JSON **estructurada** en stdout (los campos
van de primer nivel, no dentro de `message`) con una clave `event` estable:

- `event=ingestion.run.completed` (nivel `INFO`) con `ingestion_date`,
  `total_entries`, `publications_saved` y `publications_failed`.
- `event=ingestion.run.failed` (nivel `ERROR`) con el motivo en `error`.

Un **vigilante interno** (`IngestionWatchdog`, cada 5 min, en el mismo scope que
el scheduler) alerta si una de las ventanas del día pasa sin ejecutarse: emite
`event=ingestion.missed` (nivel `ERROR`) con `ingestion_date`, `window` (hora de
la ventana) y `grace_minutes`. Una ventana solo se alerta **una vez**, y no se
reclaman las anteriores al arranque del proceso (recuperarlas es del backfill,
FT00011). Para depurar, filtra stdout por `"event"`:

```sh
./gradlew :backend:run | grep -E '"event":"ingestion\.(run|missed)'
```

No hay alertas externas (email, push, webhooks) ni telemetría: el canal es el log
`ERROR` estructurado y un monitor de logs debe vigilar esas claves.

La verificación del *upsert* real (dos ejecuciones de la misma fecha sin
duplicar, con `actualizado_en` refrescado) es **opt-in** y queda fuera de
`build`/`test`/`init.sh` y de la CI:

```sh
cp .env.example .env
docker compose up -d
./gradlew :backend:flywayMigrate
DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*IngestionJobLiveTest'
docker compose down -v
```

## Backfill del histórico (`:backend:backfill`)

Carga por lotes el histórico del **último año** de **todo el sumario del BOE**
(secciones I, II.A, II.B, III, IV, V.A, V.B y V.C) reutilizando el job diario
(descarga del sumario → parseo del XML → *upsert* por `id`). Es **one-shot**
(no se cablea en el arranque del servidor ni se programa), **idempotente** (el
*upsert* no duplica) y **reanudable** (guarda su progreso en un checkpoint local).

```sh
cp .env.example .env          # define POSTGRES_PASSWORD local
docker compose up -d
./gradlew :backend:flywayMigrate
./gradlew :backend:backfill   # rango por defecto: del último año a hoy
```

La herramienta escribe **JSON estructurado** en stdout con una clave `event`
estable (`backfill.run.started`, `backfill.batch.completed`,
`backfill.run.completed`, `backfill.date.failed`); el resumen final aparece en
`backfill.run.completed` con `completed`, `failed`, `skipped` y `saved`.

Variables de entorno (todas opcionales; un valor inválido aborta con un mensaje
claro):

- `BACKFILL_FROM` (ISO `YYYY-MM-DD`; por defecto `BACKFILL_TO` menos **1 año**).
- `BACKFILL_TO` (ISO; por defecto **hoy** en `INGESTA_TIMEZONE`, `Europe/Madrid`).
- `BACKFILL_BATCH_DAYS` (por defecto `7`): días por lote de baja carga.
- `BACKFILL_DELAY_MS` (por defecto `500`): pausa entre fechas del lote.
- `BACKFILL_STATE_FILE` (por defecto `.backfill-state.txt`, ignorado por git).

El **checkpoint** es un fichero de texto local con una línea de rango
(`range=<from>..<to>`) y una fecha ISO por línea, ordenadas; se escribe de forma
**atómica**. Si falta, el backfill empieza de cero; si su rango no coincide con el
configurado, también. Un **formato inválido** falla en claro (fail-fast) para no
reanudar un backfill equivocado. Una fecha se marca **completada** solo si no hubo
error y no quedaron entradas fallidas; las fallidas se reintentan al relanzar. El
*upsert* hace seguro rehacer.

Notas de baja carga (máquina única de 8 GB): ejecútalo en horario de baja carga y
ajusta lotes/pausa por entorno. Asume el **esquema migrado**
(`:backend:flywayMigrate`). La verificación real es **opt-in** y queda fuera del
gate:

```sh
DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*BackfillLiveTest'
docker compose down -v
```

## Variables de entorno

- `POSTGRES_*` es la **fuente única de verdad**: la usa `docker-compose` y de ella
  Flyway **deriva** su URL (`jdbc:postgresql://host:port/db`).
- `FLYWAY_*` son *overrides* **opcionales** pieza a pieza (`FLYWAY_URL`
  sobrescribe la URL completa).

## Notas

- Flyway 10+ necesita el módulo `flyway-database-postgresql` en el **buildscript**
  de `:backend` (lo resuelve por ServiceLoader desde el build classpath).
- El PostgreSQL local se publica **solo en `127.0.0.1`**.
- No se versionan credenciales: si falta la contraseña, Flyway y `docker compose`
  fallan con un mensaje claro (fail-fast).
