// Flyway 10+ resuelve el dialecto PostgreSQL por ServiceLoader desde el build classpath (ver docs/desarrollo-local.md).
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
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.flyway)
    application
    alias(libs.plugins.ktor)
}

kotlin {
    jvmToolchain(21)
}

// `main()` propio, no el `EngineMain` del plugin de Ktor (que exigiría application.conf).
application {
    mainClass.set("es.aviferdev.datopublico.backend.ApplicationKt")
}

val flywayConfiguration = configurations.create("flyway")

dependencies {
    implementation(project(":shared"))

    // Corrutinas explícitas (job/scheduler de FT00009); ya llegan transitivas por
    // Ktor, pero el backend las usa directamente.
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)

    // Cliente HTTP de salida (sumario del BOE, FT00006): motor CIO + negociación.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // Persistencia JDBC (FT00008): driver PostgreSQL y pool HikariCP en el
    // classpath de producción de los repositorios (el driver también está en la
    // configuración `flyway`, que usa su propio classpath).
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)

    add(flywayConfiguration.name, libs.flyway.database.postgresql)
    add(flywayConfiguration.name, libs.postgresql)
}

val migrationDir = layout.projectDirectory.dir("src/main/resources/db/migration").asFile

// Parser mínimo `clave=valor` del `.env` de la raíz; nunca imprime valores.
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

fun resolveSetting(key: String): String? =
    System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: dotEnv[key]?.takeIf { it.isNotBlank() }

fun resolveWithDefault(vararg keys: String, default: String): String {
    keys.forEach { key -> resolveSetting(key)?.let { return it } }
    return default
}

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

// Sin default a propósito: contraseña ausente = fail-fast (nunca una credencial versionada).
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

// Se fija en ejecución para que `build` siga verde sin BD.
tasks.withType<org.flywaydb.gradle.task.AbstractFlywayTask>().configureEach {
    doFirst {
        url = flywayUrl()
        user = flywayUser()
        password = flywayPasswordOrFail()
    }
}

// Backfill one-shot del histórico (FT00011): tarea explícita, nunca enganchada a
// `build`/`check` (la CI corre sin red ni BD). Hereda el entorno del proceso.
tasks.register<JavaExec>("backfill") {
    group = "application"
    description = "Carga por lotes el histórico del BOE (one-shot, reanudable)."
    mainClass.set("es.aviferdev.datopublico.backend.ingesta.backfill.BackfillMainKt")
    classpath = sourceSets["main"].runtimeClasspath
}
