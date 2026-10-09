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
// el gate de build no debe depender de servicios externos. La conexión se
// resuelve del entorno (`FLYWAY_*`) con valores por defecto de desarrollo
// (ver `.env.example`).
val migrationDir = layout.projectDirectory.dir("src/main/resources/db/migration").asFile

flyway {
    url = System.getenv("FLYWAY_URL") ?: "jdbc:postgresql://localhost:5432/datopublico"
    user = System.getenv("FLYWAY_USER") ?: "datopublico"
    password = System.getenv("FLYWAY_PASSWORD") ?: "datopublico"
    locations = arrayOf("filesystem:${migrationDir.absolutePath}")
    configurations = arrayOf(flywayConfiguration.name)
}
