package es.aviferdev.datopublico.backend.persistence

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
 * interfaz. `save` es un *upsert* por `id` (idempotente): volver a guardar la
 * misma publicación no crea una fila nueva.
 */
interface PublicationRepository {
    /**
     * Inserta o actualiza [publication] por su `id` y devuelve el modelo
     * persistido.
     */
    fun save(publication: Publicacion): Publicacion

    /** Devuelve la publicación con [id], o `null` si no existe. */
    fun findById(id: String): Publicacion?

    /** Lista las publicaciones de una fecha ISO-8601 ([publicationDate]). */
    fun listByDate(publicationDate: String): List<Publicacion>
}

/**
 * Fila de la tabla `publicacion` (representación de persistencia).
 *
 * `section` y `category` guardan el **nombre** del enum; `deadline*` son las dos
 * columnas del [PlazoDto]. `creado_en`/`actualizado_en` los gestiona la base de
 * datos (no forman parte del modelo de dominio).
 */
data class PublicationEntity(
    val id: String,
    val title: String,
    val publicationDate: String,
    val organization: String?,
    val section: String,
    val heading: String?,
    val category: String?,
    val body: String?,
    val officialUrl: String,
    val xmlUrl: String?,
    val pdfUrl: String?,
    val rank: String?,
    val deadlineDate: String?,
    val deadlineDescription: String?,
)

/** Proyecta el modelo de dominio [Publicacion] a su [PublicationEntity]. */
internal fun Publicacion.toPublicationEntity(): PublicationEntity = PublicationEntity(
    id = id,
    title = titulo,
    publicationDate = fechaPublicacion,
    organization = organismo,
    section = seccion.name,
    heading = epigrafe,
    category = categoria?.name,
    body = texto,
    officialUrl = urlOficial,
    xmlUrl = urlXml,
    pdfUrl = urlPdf,
    rank = rango,
    deadlineDate = plazo?.fechaLimite,
    deadlineDescription = plazo?.descripcion,
)

/**
 * Reconstruye el modelo de dominio [Publicacion] desde su [PublicationEntity].
 *
 * @throws IllegalArgumentException si `section`/`category` no son nombres de
 *   enum válidos (datos corruptos: fail-fast).
 */
internal fun PublicationEntity.toDomain(): Publicacion = Publicacion(
    id = id,
    titulo = title,
    fechaPublicacion = publicationDate,
    organismo = organization,
    seccion = SeccionBoeDto.valueOf(section),
    epigrafe = heading,
    texto = body,
    urlOficial = officialUrl,
    urlXml = xmlUrl,
    urlPdf = pdfUrl,
    rango = rank,
    categoria = category?.let(CategoriaDto::valueOf),
    plazo = deadlineDate?.let { fechaLimite ->
        PlazoDto(fechaLimite = fechaLimite, descripcion = deadlineDescription)
    },
)

/**
 * Implementación JDBC de [PublicationRepository] sobre un [DataSource].
 *
 * Sin transacciones multi-repositorio: cada método abre y cierra su conexión
 * (autocommit). El pool lo gestiona [es.aviferdev.datopublico.backend.infra.Database].
 */
class PublicationRepositoryJdbc(private val dataSource: DataSource) : PublicationRepository {

    override fun save(publication: Publicacion): Publicacion {
        val entity = publication.toPublicationEntity()
        dataSource.executeUpdate(UPSERT) { statement ->
            statement.bindParameters(entity)
        }
        return publication
    }

    override fun findById(id: String): Publicacion? =
        dataSource.queryRows(
            FIND_BY_ID,
            { statement -> statement.setString(1, id) },
            { rows -> rows.toPublicationEntity() },
        ).firstOrNull()?.toDomain()

    override fun listByDate(publicationDate: String): List<Publicacion> =
        dataSource.queryRows(
            LIST_BY_DATE,
            { statement -> statement.setObject(1, LocalDate.parse(publicationDate), Types.DATE) },
            { rows -> rows.toPublicationEntity() },
        ).map { entity -> entity.toDomain() }

    /** Asigna los 14 parámetros de la sentencia de *upsert* de publicación. */
    private fun PreparedStatement.bindParameters(entity: PublicationEntity) {
        setString(1, entity.id)
        setString(2, entity.title)
        setObject(3, LocalDate.parse(entity.publicationDate), Types.DATE)
        setString(4, entity.organization)
        setString(5, entity.section)
        setString(6, entity.heading)
        setString(7, entity.category)
        setString(8, entity.body)
        setString(9, entity.officialUrl)
        setString(10, entity.xmlUrl)
        setString(11, entity.pdfUrl)
        setString(12, entity.rank)
        setObject(13, entity.deadlineDate?.let(LocalDate::parse), Types.DATE)
        setString(14, entity.deadlineDescription)
    }

    private companion object {
        const val COLUMNS = "id, titulo, fecha_publicacion, organismo, seccion, epigrafe, " +
            "categoria, texto, url_oficial, url_xml, url_pdf, rango, plazo_fecha_limite, " +
            "plazo_descripcion"

        const val UPSERT = """
            INSERT INTO publicacion ($COLUMNS)
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

        const val FIND_BY_ID = "SELECT $COLUMNS FROM publicacion WHERE id = ?"

        const val LIST_BY_DATE =
            "SELECT $COLUMNS FROM publicacion WHERE fecha_publicacion = ? ORDER BY id"
    }
}

/** Lee la fila actual del [ResultSet] como [PublicationEntity]. */
private fun ResultSet.toPublicationEntity(): PublicationEntity = PublicationEntity(
    id = getString("id"),
    title = getString("titulo"),
    publicationDate = getObject("fecha_publicacion", LocalDate::class.java).toString(),
    organization = getString("organismo"),
    section = getString("seccion"),
    heading = getString("epigrafe"),
    category = getString("categoria"),
    body = getString("texto"),
    officialUrl = getString("url_oficial"),
    xmlUrl = getString("url_xml"),
    pdfUrl = getString("url_pdf"),
    rank = getString("rango"),
    deadlineDate = getObject("plazo_fecha_limite", LocalDate::class.java)?.toString(),
    deadlineDescription = getString("plazo_descripcion"),
)
