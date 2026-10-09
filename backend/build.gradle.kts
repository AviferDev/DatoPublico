// Flyway 10+ modularizó los dialectos: la tarea resuelve el motor PostgreSQL por
// ServiceLoader desde el *build classpath* (el classloader padre del que usa la
// tarea), no desde el classpath del proyecto. Sin este `buildscript` la tarea
// falla con "No Flyway database plugin found to handle jdbc:postgresql...".
// La versión se mantiene alineada con el plugin (ver `gradle/libs.versions.toml`).
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:11.20.3")
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.flyway)
}

kotlin {
    jvmToolchain(21)
}

// Configuración dedicada a los artefactos de Flyway (driver JDBC PostgreSQL +
// módulo por motor). Aísla las dependencias de migración del classpath de
// producción; el datasource/Hikari llegan con FT00004/FT00008.
val flywayConfiguration = configurations.create("flyway")

// `:backend` depende de `:shared` desde el arranque (frontera de arquitectura).
// El plugin `application` y Ktor llegan con FT00004.
dependencies {
    implementation(project(":shared"))

    testImplementation(kotlin("test"))

    // Flyway 10+ reparte los dialectos por motor: PostgreSQL necesita el módulo
    // `flyway-database-postgresql` y el driver JDBC.
    add(flywayConfiguration.name, libs.flyway.database.postgresql)
    add(flywayConfiguration.name, libs.postgresql)
}

// Migraciones Flyway de FT00003. Las tareas son explícitas
// (`./gradlew :backend:flywayMigrate` / `:backend:flywayInfo`) y **no** se
// enganchan a `build`/`check`: la CI corre en `ubuntu-latest` sin base de datos y
// el gate de build no debe depender de servicios externos.
//
// La conexión se **deriva** de las mismas variables `POSTGRES_*` que usa
// docker-compose (host/puerto/db/usuario), con las variables `FLYWAY_*` como
// *overrides* opcionales pieza a pieza y `FLYWAY_URL` como override explícito de
// la URL completa. Así no se duplica la conexión: cambiar `POSTGRES_PORT` basta
// para que Flyway apunte al mismo sitio. Para comodidad de desarrollo se carga
// además el `.env` de la raíz del producto (ignorado por git) cuando la variable
// de entorno no está definida. **No hay contraseña por defecto**: si faltan
// `FLYWAY_PASSWORD` y `POSTGRES_PASSWORD`, la tarea Flyway falla con un mensaje
// claro (fail-fast) en lugar de conectar con una credencial implícita. La
// validación se hace en tiempo de ejecución para no romper `./gradlew build`
// (CI sin base de datos).
val migrationDir = layout.projectDirectory.dir("src/main/resources/db/migration").asFile

// Parser mínimo `clave=valor` del `.env` de la raíz. Nunca imprime valores.
fun loadDotEnv(): Map<String, String> {
    val envFile = rootDir.resolve(".env")
    if (!envFile.isFile) return emptyMap()
    return envFile.readLines()
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
        .associate { line ->
            val key = line.substringBefore('=').trim().removePrefix("export ").trim()
            val value = line.substringAfter('=').trim().trim('"', '\'')
            key to value
        }
}

val dotEnv: Map<String, String> = loadDotEnv()

// Prioridad: variable de entorno > `.env` > valor por defecto no sensible.
fun resolveSetting(key: String): String? =
    System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: dotEnv[key]?.takeIf { it.isNotBlank() }

// Devuelve el primer valor resuelto de las claves dadas o el default. Se usa
// para derivar la conexión: `FLYWAY_*` (override) > `POSTGRES_*` (fuente única
// compartida con docker-compose) > default de desarrollo.
fun resolveWithDefault(vararg keys: String, default: String): String {
    keys.forEach { key -> resolveSetting(key)?.let { return it } }
    return default
}

// La URL se **deriva** de las piezas salvo que `FLYWAY_URL` la sobrescriba
// explícitamente. Así, cambiar `POSTGRES_PORT` (o host/db) basta para que Flyway
// apunte al mismo sitio que docker-compose, sin duplicar la conexión.
fun flywayUrl(): String =
    resolveSetting("FLYWAY_URL")
        ?: buildString {
            append("jdbc:postgresql://")
            append(resolveWithDefault("FLYWAY_HOST", "POSTGRES_HOST", default = "localhost"))
            append(':')
            append(resolveWithDefault("FLYWAY_PORT", "POSTGRES_PORT", default = "5432"))
            append('/')
            append(resolveWithDefault("FLYWAY_DB", "POSTGRES_DB", default = "datopublico"))
        }

fun flywayUser(): String =
    resolveWithDefault("FLYWAY_USER", "POSTGRES_USER", default = "datopublico")

// Sin valor por defecto a propósito: una contraseña ausente (ni `FLYWAY_PASSWORD`
// ni `POSTGRES_PASSWORD`) debe fallar, nunca caer en una credencial versionada.
fun flywayPasswordOrFail(): String =
    resolveSetting("FLYWAY_PASSWORD")
        ?: resolveSetting("POSTGRES_PASSWORD")
        ?: error(
            "Contraseña de base de datos no definida: define POSTGRES_PASSWORD " +
                "en .env (o expórtala, o usa FLYWAY_PASSWORD como override)"
        )

flyway {
    url = flywayUrl()
    user = flywayUser()
    locations = arrayOf("filesystem:${migrationDir.absolutePath}")
    configurations = arrayOf(flywayConfiguration.name)
}

// La conexión se fija en ejecución: así `./gradlew build` sigue verde sin `.env`
// ni base de datos, y las tareas Flyway fallan con el mensaje claro si falta
// `FLYWAY_PASSWORD`.
tasks.withType<org.flywaydb.gradle.task.AbstractFlywayTask>().configureEach {
    doFirst {
        url = flywayUrl()
        user = flywayUser()
        password = flywayPasswordOrFail()
    }
}
