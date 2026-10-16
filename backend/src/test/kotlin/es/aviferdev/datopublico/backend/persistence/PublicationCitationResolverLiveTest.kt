package es.aviferdev.datopublico.backend.persistence

import es.aviferdev.datopublico.backend.chat.PublicationCitationResolver
import es.aviferdev.datopublico.backend.ingestion.publication.Publication
import es.aviferdev.datopublico.backend.infra.Database
import es.aviferdev.datopublico.backend.infra.DatabaseConfig
import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.BoeSectionDto
import java.io.File
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Prueba **opt-in** de la resolución **real** de la cita contra el PostgreSQL de
 * `docker-compose` (escenario 9 del spec): [PublicationCitationResolver] inserta
 * una publicación y resuelve su cita (`publicationId`, título y `urlOficial`); un
 * id inexistente devuelve `null`.
 *
 * Se salta por defecto (`DB_LIVE_TEST != 1`) para que el gate y la CI **no**
 * dependan de una base de datos. Requiere el esquema migrado (`V1`…`V5`,
 * `./gradlew :backend:flywayMigrate`) y una `.env` o el entorno con `POSTGRES_*`.
 * Para ejecutarla:
 *
 * ```
 * DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*PublicationCitationResolverLiveTest'
 * ```
 */
class PublicationCitationResolverLiveTest {

    @Test
    fun `resolves the citation of a persisted publication and null for an unknown id`() {
        if (System.getenv("DB_LIVE_TEST") != "1") {
            println("PublicationCitationResolverLiveTest omitido: exporta DB_LIVE_TEST=1 para el test real.")
        } else {
            val dataSource = Database.createDataSource(DatabaseConfig.fromEnv(environment()))
            try {
                clean(dataSource)
                try {
                    runLive(dataSource)
                } finally {
                    clean(dataSource)
                }
            } finally {
                (dataSource as AutoCloseable).close()
            }
        }
    }

    /** Persiste una publicación y comprueba la cita resuelta y el id ausente. */
    private fun runLive(dataSource: DataSource) {
        val repository = PublicationRepositoryJdbc(dataSource)
        repository.save(publication())
        val resolver = PublicationCitationResolver(repository)

        val citation = resolver.resolve(PUBLICATION_ID)
        assertEquals(PUBLICATION_ID, citation?.publicationId)
        assertEquals("Real Decreto de prueba del chat", citation?.title)
        assertEquals(URL, citation?.officialUrl)
        assertNull(resolver.resolve("FT00031-LIVE-AUSENTE"))
        println("PublicationCitationResolverLiveTest: cita resuelta y ausente OK")
    }

    private fun publication(): Publication = Publication(
        id = PUBLICATION_ID,
        title = "Real Decreto de prueba del chat",
        publicationDate = DATE,
        issuingBody = null,
        section = BoeSectionDto.I,
        heading = null,
        body = null,
        officialUrl = URL,
        xmlUrl = null,
        pdfUrl = null,
        rank = "Real Decreto",
        category = CategoryDto.NORMS_AND_LEGISLATION,
    )

    /** Borra la publicación de prueba. */
    private fun clean(dataSource: DataSource) {
        dataSource.executeUpdate("DELETE FROM publicacion WHERE id = ?") { statement ->
            statement.setString(1, PUBLICATION_ID)
        }
    }

    /**
     * Entorno del test: variables del proceso más, si falta alguna, las de la
     * `.env` de la raíz del producto (sin exportar).
     */
    private fun environment(): Map<String, String> {
        val environment = System.getenv().toMutableMap()
        val dotEnv = listOf(File(".env"), File("../.env")).firstOrNull { file -> file.isFile }
        dotEnv?.readLines()
            ?.asSequence()
            ?.map { line -> line.trim() }
            ?.filter { line -> line.isNotEmpty() && !line.startsWith("#") && line.contains('=') }
            ?.forEach { line ->
                val key = line.substringBefore('=').trim()
                environment.putIfAbsent(key, line.substringAfter('=').trim().trim('"', '\''))
            }
        return environment
    }

    private companion object {
        const val DATE = "2026-10-11"
        const val PUBLICATION_ID = "FT00031-LIVE-CHAT"
        const val URL = "https://www.boe.es/diario_boe/txt.php?id=FT00031-LIVE-CHAT"
    }
}
