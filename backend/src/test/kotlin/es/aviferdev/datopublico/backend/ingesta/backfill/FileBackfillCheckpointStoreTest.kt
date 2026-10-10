package es.aviferdev.datopublico.backend.ingesta.backfill

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests de gate de [FileBackfillCheckpointStore] (escenario 5): round-trip,
 * fichero ausente, formato inválido (fail-fast) y escritura atómica sin restos.
 */
class FileBackfillCheckpointStoreTest {

    @Test
    fun `round-trips a checkpoint`() = withTempDir { dir ->
        val store = FileBackfillCheckpointStore(dir.resolve("state.txt"))
        val checkpoint = BackfillCheckpoint(
            from = DATE,
            to = DATE.plusDays(4),
            completedDates = setOf(DATE.plusDays(2), DATE),
        )

        store.save(checkpoint)

        assertEquals(checkpoint, store.load())
    }

    @Test
    fun `returns null when the file does not exist`() = withTempDir { dir ->
        val store = FileBackfillCheckpointStore(dir.resolve("ausente.txt"))

        assertEquals(null, store.load())
    }

    @Test
    fun `writes a range line and the completed dates sorted`() = withTempDir { dir ->
        val path = dir.resolve("state.txt")
        FileBackfillCheckpointStore(path).save(
            BackfillCheckpoint(
                from = DATE,
                to = DATE.plusDays(5),
                completedDates = setOf(DATE.plusDays(4), DATE.plusDays(1)),
            )
        )

        assertEquals(
            listOf("range=2024-01-01..2024-01-06", "2024-01-02", "2024-01-05"),
            Files.readAllLines(path),
        )
    }

    @Test
    fun `fails fast when the range line is missing`() = withTempDir { dir ->
        val path = dir.resolve("state.txt")
        Files.write(path, listOf("no-es-un-rango", "2024-01-01"))

        val error = assertFailsWith<IllegalStateException> {
            FileBackfillCheckpointStore(path).load()
        }

        assertTrue(error.message!!.contains("Checkpoint de backfill inválido"), error.message!!)
    }

    @Test
    fun `fails fast when a date is unreadable`() = withTempDir { dir ->
        val path = dir.resolve("state.txt")
        Files.write(path, listOf("range=2024-01-01..2024-01-06", "no-es-fecha"))

        assertFailsWith<IllegalStateException> { FileBackfillCheckpointStore(path).load() }
    }

    @Test
    fun `replaces the previous state without leaving temporary files`() = withTempDir { dir ->
        val path = dir.resolve("state.txt")
        val store = FileBackfillCheckpointStore(path)

        store.save(BackfillCheckpoint(DATE, DATE.plusDays(2), setOf(DATE)))
        store.save(
            BackfillCheckpoint(DATE, DATE.plusDays(2), setOf(DATE, DATE.plusDays(1)))
        )

        assertEquals(setOf(DATE, DATE.plusDays(1)), store.load()?.completedDates)
        val files = Files.list(dir).use { paths -> paths.count() }
        assertEquals(1L, files, "solo debe quedar el fichero de estado, sin temporales")
    }

    private fun withTempDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("backfill-checkpoint-")
        try {
            block(dir)
        } finally {
            deleteRecursively(dir)
        }
    }

    private fun deleteRecursively(dir: Path) {
        Files.walk(dir).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2024, 1, 1)
    }
}
