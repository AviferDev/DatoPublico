package es.aviferdev.datopublico.backend.persistencia

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.PlazoDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.LocalDate
import javax.sql.DataSource

/**
 * Persistencia de [Publicacion] en la tabla `publicacion`.
 *
 * La ingesta (FT00009) y la clasificación (FT00012) escriben a través de esta
 * interfaz. `guardar` es un *upsert* por `id` (idempotente): volver a guardar la
 * misma publicación no crea una fila nueva.
 */
interface PublicacionRepositorio {
    /**
     * Inserta o actualiza [publicacion] por su `id` y devuelve el modelo
     * persistido.
     */
    fun guardar(publicacion: Publicacion): Publicacion

    /** Devuelve la publicación con [id], o `null` si no existe. */
    fun buscarPorId(id: String): Publicacion?

    /** Lista las publicaciones de una fecha ISO-8601 ([fechaPublicacion]). */
    fun listarPorFecha(fechaPublicacion: String): List<Publicacion>
}

/**
 * Fila de la tabla `publicacion` (representación de persistencia).
 *
 * `seccion` y `categoria` guardan el **nombre** del enum; `plazo_*` son las dos
 * columnas del [PlazoDto]. `creado_en`/`actualizado_en` los gestiona la base de
 * datos (no forman parte del modelo de dominio).
 */
data class PublicacionEntity(
    val id: String,
    val titulo: String,
    val fechaPublicacion: String,
    val organismo: String?,
    val seccion: String,
    val epigrafe: String?,
    val categoria: String?,
    val texto: String?,
    val urlOficial: String,
    val urlXml: String?,
    val urlPdf: String?,
    val rango: String?,
    val plazoFechaLimite: String?,
    val plazoDescripcion: String?,
)

/** Proyecta el modelo de dominio [Publicacion] a su [PublicacionEntity]. */
internal fun Publicacion.aPublicacionEntity(): PublicacionEntity = PublicacionEntity(
    id = id,
    titulo = titulo,
    fechaPublicacion = fechaPublicacion,
    organismo = organismo,
    seccion = seccion.name,
    epigrafe = epigrafe,
    categoria = categoria?.name,
    texto = texto,
    urlOficial = urlOficial,
    urlXml = urlXml,
    urlPdf = urlPdf,
    rango = rango,
    plazoFechaLimite = plazo?.fechaLimite,
    plazoDescripcion = plazo?.descripcion,
)

/**
 * Reconstruye el modelo de dominio [Publicacion] desde su [PublicacionEntity].
 *
 * @throws IllegalArgumentException si `seccion`/`categoria` no son nombres de
 *   enum válidos (datos corruptos: fail-fast).
 */
internal fun PublicacionEntity.aPublicacion(): Publicacion = Publicacion(
    id = id,
    titulo = titulo,
    fechaPublicacion = fechaPublicacion,
    organismo = organismo,
    seccion = SeccionBoeDto.valueOf(seccion),
    epigrafe = epigrafe,
    texto = texto,
    urlOficial = urlOficial,
    urlXml = urlXml,
    urlPdf = urlPdf,
    rango = rango,
    categoria = categoria?.let(CategoriaDto::valueOf),
    plazo = plazoFechaLimite?.let { fechaLimite ->
        PlazoDto(fechaLimite = fechaLimite, descripcion = plazoDescripcion)
    },
)

/**
 * Implementación JDBC de [PublicacionRepositorio] sobre un [DataSource].
 *
 * Sin transacciones multi-repositorio: cada método abre y cierra su conexión
 * (autocommit). El pool lo gestiona [es.aviferdev.datopublico.backend.infra.BaseDatos].
 */
class PublicacionRepositorioJdbc(private val dataSource: DataSource) : PublicacionRepositorio {

    override fun guardar(publicacion: Publicacion): Publicacion {
        val entidad = publicacion.aPublicacionEntity()
        dataSource.ejecutarActualizacion(INSERTAR_O_ACTUALIZAR) { sentencia ->
            sentencia.fijarParametros(entidad)
        }
        return publicacion
    }

    override fun buscarPorId(id: String): Publicacion? =
        dataSource.consultarFilas(
            BUSCAR_POR_ID,
            { sentencia -> sentencia.setString(1, id) },
            { filas -> filas.aPublicacionEntity() },
        ).firstOrNull()?.aPublicacion()

    override fun listarPorFecha(fechaPublicacion: String): List<Publicacion> =
        dataSource.consultarFilas(
            LISTAR_POR_FECHA,
            { sentencia -> sentencia.setObject(1, LocalDate.parse(fechaPublicacion), Types.DATE) },
            { filas -> filas.aPublicacionEntity() },
        ).map { entidad -> entidad.aPublicacion() }

    /** Asigna los 14 parámetros de la sentencia de *upsert* de publicación. */
    private fun PreparedStatement.fijarParametros(entidad: PublicacionEntity) {
        setString(1, entidad.id)
        setString(2, entidad.titulo)
        setObject(3, LocalDate.parse(entidad.fechaPublicacion), Types.DATE)
        setString(4, entidad.organismo)
        setString(5, entidad.seccion)
        setString(6, entidad.epigrafe)
        setString(7, entidad.categoria)
        setString(8, entidad.texto)
        setString(9, entidad.urlOficial)
        setString(10, entidad.urlXml)
        setString(11, entidad.urlPdf)
        setString(12, entidad.rango)
        setObject(13, entidad.plazoFechaLimite?.let(LocalDate::parse), Types.DATE)
        setString(14, entidad.plazoDescripcion)
    }

    private companion object {
        const val COLUMNAS = "id, titulo, fecha_publicacion, organismo, seccion, epigrafe, " +
            "categoria, texto, url_oficial, url_xml, url_pdf, rango, plazo_fecha_limite, " +
            "plazo_descripcion"

        const val INSERTAR_O_ACTUALIZAR = """
            INSERT INTO publicacion ($COLUMNAS)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                titulo = EXCLUDED.titulo,
                fecha_publicacion = EXCLUDED.fecha_publicacion,
                organismo = EXCLUDED.organismo,
                seccion = EXCLUDED.seccion,
                epigrafe = EXCLUDED.epigrafe,
                categoria = EXCLUDED.categoria,
                texto = EXCLUDED.texto,
                url_oficial = EXCLUDED.url_oficial,
                url_xml = EXCLUDED.url_xml,
                url_pdf = EXCLUDED.url_pdf,
                rango = EXCLUDED.rango,
                plazo_fecha_limite = EXCLUDED.plazo_fecha_limite,
                plazo_descripcion = EXCLUDED.plazo_descripcion,
                actualizado_en = now()
        """

        const val BUSCAR_POR_ID = "SELECT $COLUMNAS FROM publicacion WHERE id = ?"

        const val LISTAR_POR_FECHA =
            "SELECT $COLUMNAS FROM publicacion WHERE fecha_publicacion = ? ORDER BY id"
    }
}

/** Lee la fila actual del [ResultSet] como [PublicacionEntity]. */
private fun ResultSet.aPublicacionEntity(): PublicacionEntity = PublicacionEntity(
    id = getString("id"),
    titulo = getString("titulo"),
    fechaPublicacion = getObject("fecha_publicacion", LocalDate::class.java).toString(),
    organismo = getString("organismo"),
    seccion = getString("seccion"),
    epigrafe = getString("epigrafe"),
    categoria = getString("categoria"),
    texto = getString("texto"),
    urlOficial = getString("url_oficial"),
    urlXml = getString("url_xml"),
    urlPdf = getString("url_pdf"),
    rango = getString("rango"),
    plazoFechaLimite = getObject("plazo_fecha_limite", LocalDate::class.java)?.toString(),
    plazoDescripcion = getString("plazo_descripcion"),
)
