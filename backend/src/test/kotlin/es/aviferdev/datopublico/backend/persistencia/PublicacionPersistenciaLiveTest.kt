package es.aviferdev.datopublico.backend.persistencia

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.infra.BaseDatos
import es.aviferdev.datopublico.backend.infra.ConfiguracionBd
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.PlazoDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import java.io.File
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Prueba **opt-in** contra el PostgreSQL real de `docker-compose` (escenarios
 * 1–5 del spec).
 *
 * Se salta por defecto (`DB_LIVE_TEST != 1`) para que el gate y la CI **no**
 * dependan de una base de datos. Requiere el esquema migrado
 * (`./gradlew :backend:flywayMigrate`) y una `.env` o el entorno con
 * `POSTGRES_*`. Para ejecutarla:
 *
 * ```
 * DB_LIVE_TEST=1 ./gradlew :backend:test --tests '*PublicacionPersistenciaLiveTest'
 * ```
 */
class PublicacionPersistenciaLiveTest {

    @Test
    fun `guarda y recupera publicaciones, upsert, nulos y cascada`() {
        if (System.getenv("DB_LIVE_TEST") != "1") {
            println("PublicacionPersistenciaLiveTest omitido: exporta DB_LIVE_TEST=1 para el test real.")
        } else {
            val dataSource = BaseDatos.crearDataSource(ConfiguracionBd.fromEnv(entorno()))
            try {
                comprobarTablas(dataSource)
                comprobarGuardarYRecuperar(dataSource)
                comprobarUpsert(dataSource)
                comprobarNulos(dataSource)
                comprobarCascada(dataSource)
            } finally {
                (dataSource as AutoCloseable).close()
            }
        }
    }

    private fun comprobarTablas(dataSource: DataSource) {
        val tablas = dataSource.consultarFilas(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            { },
            { filas -> filas.getString("table_name") },
        )
        assertTrue(
            tablas.containsAll(listOf("publicacion", "fragmento", "resumen", "cola_revision")),
            "faltan tablas de dominio: $tablas",
        )
    }

    private fun comprobarGuardarYRecuperar(dataSource: DataSource) {
        val repositorio = PublicacionRepositorioJdbc(dataSource)
        val publicacion = publicacionCompleta(ID_COMPLETA)
        try {
            repositorio.guardar(publicacion)

            val recuperada = repositorio.buscarPorId(ID_COMPLETA)
            assertEquals(publicacion, recuperada)
            assertEquals(1, repositorio.listarPorFecha(FECHA).size)
        } finally {
            dataSource.eliminarPublicacion(ID_COMPLETA)
        }
    }

    private fun comprobarUpsert(dataSource: DataSource) {
        val repositorio = PublicacionRepositorioJdbc(dataSource)
        try {
            repositorio.guardar(publicacionCompleta(ID_UPSERT))
            val antes = dataSource.actualizadoEn(ID_UPSERT)
            Thread.sleep(10)
            repositorio.guardar(publicacionCompleta(ID_UPSERT).copy(titulo = "Título actualizado"))

            assertEquals(1, dataSource.contarPublicaciones(ID_UPSERT))
            assertEquals("Título actualizado", repositorio.buscarPorId(ID_UPSERT)?.titulo)
            assertTrue(dataSource.actualizadoEn(ID_UPSERT).isAfter(antes))
        } finally {
            dataSource.eliminarPublicacion(ID_UPSERT)
        }
    }

    private fun comprobarNulos(dataSource: DataSource) {
        val repositorio = PublicacionRepositorioJdbc(dataSource)
        val publicacion = publicacionConNulos(ID_NULOS)
        try {
            repositorio.guardar(publicacion)

            assertEquals(publicacion, repositorio.buscarPorId(ID_NULOS))
        } finally {
            dataSource.eliminarPublicacion(ID_NULOS)
        }
    }

    private fun comprobarCascada(dataSource: DataSource) {
        val publicaciones = PublicacionRepositorioJdbc(dataSource)
        val fragmentos = FragmentoRepositorioJdbc(dataSource)
        val resumenes = ResumenRepositorioJdbc(dataSource)
        val cola = ColaRevisionRepositorioJdbc(dataSource)
        try {
            publicaciones.guardar(publicacionConNulos(ID_CASCADA))
            val fragmento = fragmentos.guardar(
                FragmentoEntity(
                    publicacionId = ID_CASCADA,
                    orden = 1,
                    referencia = "Artículo 1",
                    contenido = "Contenido del fragmento",
                )
            )
            val resumen = resumenes.guardar(
                ResumenEntity(
                    publicacionId = ID_CASCADA,
                    queCambia = "Cambia algo",
                    aQuienAfecta = "Afecta a alguien",
                    cifrasClave = listOf("1", "2"),
                    fuenteOficial = "https://www.boe.es/diario_boe/txt.php?id=$ID_CASCADA",
                )
            )
            val resumenId = requireNotNull(resumen.id) { "el resumen no recibió id" }
            val entrada = cola.guardar(ColaRevisionEntity(resumenId = resumenId, motivo = "Confianza baja"))

            assertNotNull(fragmento.id)
            assertNotNull(entrada.id)
            assertEquals(1, fragmentos.listarPorPublicacion(ID_CASCADA).size)
            assertEquals(listOf("1", "2"), resumenes.buscarPorPublicacion(ID_CASCADA)?.cifrasClave)
            assertEquals(1, cola.listarPorResumen(resumenId).size)

            dataSource.eliminarPublicacion(ID_CASCADA)

            assertEquals(0, fragmentos.listarPorPublicacion(ID_CASCADA).size)
            assertNull(resumenes.buscarPorPublicacion(ID_CASCADA))
            assertEquals(0, cola.listarPorResumen(resumenId).size)
        } finally {
            dataSource.eliminarPublicacion(ID_CASCADA)
        }
    }

    private fun publicacionCompleta(id: String): Publicacion = Publicacion(
        id = id,
        titulo = "Resolución de 8 de octubre de 2026",
        fechaPublicacion = FECHA,
        organismo = "MINISTERIO DE EDUCACIÓN",
        seccion = SeccionBoeDto.II_A,
        epigrafe = "Becas y subvenciones",
        texto = "Primero. Convocar...\nSegundo. El plazo...",
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=$id",
        urlXml = "https://www.boe.es/diario_boe/xml.php?id=$id",
        urlPdf = "https://www.boe.es/boe/dias/2026/10/09/pdfs/A00001-00002.pdf",
        rango = "Resolución",
        categoria = CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS,
        plazo = PlazoDto(fechaLimite = "2026-11-30", descripcion = "Diez días hábiles"),
    )

    private fun publicacionConNulos(id: String): Publicacion = Publicacion(
        id = id,
        titulo = "Resolución sin metadatos enriquecidos",
        fechaPublicacion = FECHA,
        organismo = null,
        seccion = SeccionBoeDto.II_A,
        epigrafe = null,
        texto = null,
        urlOficial = "https://www.boe.es/diario_boe/txt.php?id=$id",
        urlXml = null,
        urlPdf = null,
        rango = null,
    )

    /**
     * Entorno del test: variables del proceso más, si falta alguna, las de la
     * `.env` de la raíz del producto (sin exportar). Así el comando opt-in
     * funciona con solo `docker compose up -d` y `.env`.
     */
    private fun entorno(): Map<String, String> {
        val entorno = System.getenv().toMutableMap()
        val dotEnv = listOf(File(".env"), File("../.env")).firstOrNull { archivo -> archivo.isFile }
        dotEnv?.readLines()
            ?.asSequence()
            ?.map { linea -> linea.trim() }
            ?.filter { linea -> linea.isNotEmpty() && !linea.startsWith("#") && linea.contains('=') }
            ?.forEach { linea ->
                val clave = linea.substringBefore('=').trim()
                entorno.putIfAbsent(clave, linea.substringAfter('=').trim().trim('"', '\''))
            }
        return entorno
    }

    private fun DataSource.eliminarPublicacion(id: String) {
        ejecutarActualizacion("DELETE FROM publicacion WHERE id = ?") { sentencia ->
            sentencia.setString(1, id)
        }
    }

    private fun DataSource.contarPublicaciones(id: String): Long =
        consultarFilas(
            "SELECT count(*) AS total FROM publicacion WHERE id = ?",
            { sentencia -> sentencia.setString(1, id) },
            { filas -> filas.getLong("total") },
        ).first()

    private fun DataSource.actualizadoEn(id: String): Instant =
        consultarFilas(
            "SELECT actualizado_en FROM publicacion WHERE id = ?",
            { sentencia -> sentencia.setString(1, id) },
            { filas -> filas.aInstant("actualizado_en") },
        ).first()

    private fun ResultSet.aInstant(columna: String): Instant =
        getObject(columna, OffsetDateTime::class.java).toInstant()

    private companion object {
        const val FECHA = "2026-10-09"
        const val ID_COMPLETA = "FT00008-LIVE-COMPLETA"
        const val ID_UPSERT = "FT00008-LIVE-UPSERT"
        const val ID_NULOS = "FT00008-LIVE-NULOS"
        const val ID_CASCADA = "FT00008-LIVE-CASCADA"
    }
}
