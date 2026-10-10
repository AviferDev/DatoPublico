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
