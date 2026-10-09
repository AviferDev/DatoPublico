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
// La conexión se resuelve del entorno (`FLYWAY_*`). Para comodidad de desarrollo
// se carga además el `.env` de la raíz del producto (ignorado por git) cuando la
// variable de entorno no está definida. **No hay contraseña por defecto**: si
// `FLYWAY_PASSWORD` falta, la tarea Flyway falla con un mensaje claro (fail-fast)
// en lugar de conectar con una credencial implícita. La validación se hace en
// tiempo de ejecución para no romper `./gradlew build` (CI sin base de datos).
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

fun flywayUrl(): String =
    resolveSetting("FLYWAY_URL") ?: "jdbc:postgresql://localhost:5432/datopublico"

fun flywayUser(): String =
    resolveSetting("FLYWAY_USER") ?: "datopublico"

// Sin valor por defecto a propósito: un `FLYWAY_PASSWORD` ausente debe fallar,
// nunca caer en una credencial versionada.
fun flywayPasswordOrFail(): String =
    resolveSetting("FLYWAY_PASSWORD")
        ?: error("FLYWAY_PASSWORD no definido: copia .env.example a .env o expórtalo")

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
