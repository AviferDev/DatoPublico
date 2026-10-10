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
y su índice HNSW llegan con FT00016, y los valores de `categoria`/`plazo` con
FT00012 (columnas nullable). El driver PostgreSQL y HikariCP están en el
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
