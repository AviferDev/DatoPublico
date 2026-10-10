package es.aviferdev.datopublico.backend.ingesta.backfill

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Implementación de [BackfillCheckpointStore] en un **fichero de texto local**.
 *
 * Formato: una línea `range=<from>..<to>` y una fecha ISO por línea, ordenadas.
 * La escritura es **atómica** (fichero temporal en el mismo directorio +
 * `Files.move` con `ATOMIC_MOVE`) para que una interrupción no deje el estado a
 * medias. Fichero **ausente** → `null`; formato **inválido** → fail-fast con un
 * mensaje claro, para no reanudar un backfill silenciosamente equivocado. Un
 * fallo de **escritura** se registra como aviso y **no** aborta el run (el
 * *upsert* de publicación hace seguro rehacer).
 *
 * @param path ruta del fichero de estado.
 * @param logger logger del aviso de escritura.
 */
class FileBackfillCheckpointStore(
    private val path: Path,
    private val logger: Logger = LoggerFactory.getLogger(FileBackfillCheckpointStore::class.java),
) : BackfillCheckpointStore {

    override fun load(): BackfillCheckpoint? =
        if (Files.isRegularFile(path)) parse(Files.readAllLines(path)) else null

    override fun save(checkpoint: BackfillCheckpoint) {
        try {
            writeAtomically(render(checkpoint))
        } catch (error: IOException) {
            logger.warn(
                "No se pudo guardar el checkpoint de backfill en {}: {}",
                path,
                error.message,
            )
        }
    }

    /** Renderiza el checkpoint a las líneas del fichero (fechas ordenadas). */
    private fun render(checkpoint: BackfillCheckpoint): List<String> = buildList {
        add("$RANGE_PREFIX${checkpoint.from}$RANGE_SEPARATOR${checkpoint.to}")
        checkpoint.completedDates.sorted().forEach { date -> add(date.toString()) }
    }

    /** Escritura atómica: temporal en el mismo directorio y `move`. */
    private fun writeAtomically(lines: List<String>) {
        val directory = path.toAbsolutePath().parent
        Files.createDirectories(directory)
        val temp = Files.createTempFile(directory, TEMP_PREFIX, TEMP_SUFFIX)
        Files.write(temp, lines)
        Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE)
    }

    /** Parsea las líneas del fichero; cualquier anomalía es fail-fast. */
    private fun parse(lines: List<String>): BackfillCheckpoint {
        val rangeLine = lines.firstOrNull { line -> line.startsWith(RANGE_PREFIX) }
            ?: throw invalid("falta la línea '$RANGE_PREFIX<from>$RANGE_SEPARATOR<to>'")
        val (from, to) = parseRange(rangeLine.removePrefix(RANGE_PREFIX))
        return BackfillCheckpoint(from = from, to = to, completedDates = completedDates(lines))
    }

    /** Fechas del fichero (todas las líneas que no son el rango). */
    private fun completedDates(lines: List<String>): Set<LocalDate> =
        lines.asSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotEmpty() && !line.startsWith(RANGE_PREFIX) }
            .map(::parseDate)
            .toSet()

    private fun parseRange(value: String): Pair<LocalDate, LocalDate> {
        val parts = value.split(RANGE_SEPARATOR)
        val range = parts.takeIf { it.size == 2 }?.let { (from, to) ->
            parseDate(from.trim()) to parseDate(to.trim())
        }
        return range ?: throw invalid("rango ilegible: '$value'")
    }

    private fun parseDate(raw: String): LocalDate =
        runCatching { LocalDate.parse(raw.trim()) }.getOrElse {
            throw invalid("fecha ilegible: '$raw'")
        }

    private fun invalid(reason: String): IllegalStateException =
        IllegalStateException("Checkpoint de backfill inválido en $path: $reason")

    private companion object {
        const val RANGE_PREFIX = "range="
        const val RANGE_SEPARATOR = ".."
        const val TEMP_PREFIX = ".backfill-state-"
        const val TEMP_SUFFIX = ".tmp"
    }
}
