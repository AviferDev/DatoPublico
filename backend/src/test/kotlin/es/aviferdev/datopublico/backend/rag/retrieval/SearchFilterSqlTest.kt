package es.aviferdev.datopublico.backend.rag.retrieval

import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.BoeSectionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests **puros** (sin red ni base de datos) de [SearchFilterSql]: cláusula SQL,
 * parámetros y su **orden documentado**, fechas con *cast* `?::date` y fail-fast
 * de fecha no ISO-8601 (escenarios 2, 4 y 5 del spec).
 */
class SearchFilterSqlTest {

    @Test
    fun `an empty filter produces no clause and no parameters`() {
        val filter = SearchFilter()

        assertEquals("", SearchFilterSql.whereClause(filter))
        assertEquals(emptyList(), SearchFilterSql.parameters(filter))
    }

    @Test
    fun `a category filter compares the enum name`() {
        val filter = SearchFilter(category = CategoryDto.PUBLIC_EMPLOYMENT_AND_JOB_OFFERS)

        assertEquals(" AND p.categoria = ?", SearchFilterSql.whereClause(filter))
        assertEquals(listOf(CategoryDto.PUBLIC_EMPLOYMENT_AND_JOB_OFFERS.name), SearchFilterSql.parameters(filter))
    }

    @Test
    fun `a section filter compares the enum name`() {
        val filter = SearchFilter(section = BoeSectionDto.II_B)

        assertEquals(" AND p.seccion = ?", SearchFilterSql.whereClause(filter))
        assertEquals(listOf(BoeSectionDto.II_B.name), SearchFilterSql.parameters(filter))
    }

    @Test
    fun `an organization filter compares the exact value`() {
        val filter = SearchFilter(organization = "Ministerio de Hacienda")

        assertEquals(" AND p.organismo = ?", SearchFilterSql.whereClause(filter))
        assertEquals(listOf("Ministerio de Hacienda"), SearchFilterSql.parameters(filter))
    }

    @Test
    fun `a date filter binds the value as text with a date cast`() {
        val filter = SearchFilter(publishedFrom = "2026-10-01")

        val clause = SearchFilterSql.whereClause(filter)

        assertEquals(" AND p.fecha_publicacion >= ?::date", clause)
        assertFalse(clause.contains("2026-10-01"), "la fecha no debe concatenarse en la cláusula: $clause")
        assertEquals(listOf("2026-10-01"), SearchFilterSql.parameters(filter))
    }

    @Test
    fun `combined filters keep the documented order in clause and parameters`() {
        val filter = SearchFilter(
            publishedFrom = "2026-10-01",
            publishedTo = "2026-10-31",
            category = CategoryDto.PUBLIC_EMPLOYMENT_AND_JOB_OFFERS,
            section = BoeSectionDto.II_B,
            organization = "Ministerio de Hacienda",
        )

        val clause = SearchFilterSql.whereClause(filter)
        val parameters = SearchFilterSql.parameters(filter)

        assertEquals(
            " AND p.fecha_publicacion >= ?::date" +
                " AND p.fecha_publicacion <= ?::date" +
                " AND p.categoria = ?" +
                " AND p.seccion = ?" +
                " AND p.organismo = ?",
            clause,
        )
        assertEquals(
            listOf(
                "2026-10-01",
                "2026-10-31",
                CategoryDto.PUBLIC_EMPLOYMENT_AND_JOB_OFFERS.name,
                BoeSectionDto.II_B.name,
                "Ministerio de Hacienda",
            ),
            parameters,
        )
        assertEquals(
            clause.count { character -> character == '?' },
            parameters.size,
            "debe haber un parámetro por cada placeholder de la cláusula",
        )
    }

    @Test
    fun `a non ISO date fails fast in both functions`() {
        val from = SearchFilter(publishedFrom = "01/10/2026")
        val to = SearchFilter(publishedTo = "2026-13-01")

        assertFailsWith<IllegalArgumentException> { SearchFilterSql.whereClause(from) }
        assertFailsWith<IllegalArgumentException> { SearchFilterSql.parameters(from) }
        assertFailsWith<IllegalArgumentException> { SearchFilterSql.whereClause(to) }
        assertFailsWith<IllegalArgumentException> { SearchFilterSql.parameters(to) }
    }

    @Test
    fun `a partial date filter still produces a coherent clause and parameters`() {
        val filter = SearchFilter(publishedTo = "2026-10-31")

        val clause = SearchFilterSql.whereClause(filter)
        val parameters = SearchFilterSql.parameters(filter)

        assertTrue(clause.contains("<= ?::date"), "debe filtrar el límite superior: $clause")
        assertEquals(listOf("2026-10-31"), parameters)
    }
}
