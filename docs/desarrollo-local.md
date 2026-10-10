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
(`infra/Database`, tamaño ≤5). **No** se cablea en el arranque del servidor: la
crea y la cierra quien la consume (job o test).

La migración `V2__persistencia_publicaciones.sql` crea las tablas `publicacion`,
`fragmento`, `resumen` y `cola_revision`; el embedding `vector(384)` de `fragmento`
y su índice HNSW llegan con FT00016, y los valores de `categoria`/`plazo` los
produce la ingesta desde FT00012 (columnas nullable). El driver PostgreSQL y
HikariCP están en el classpath de producción de `:backend`.

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
