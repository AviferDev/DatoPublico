package es.aviferdev.datopublico.backend.rag.retrieval

import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.BoeSectionDto

/**
 * Filtro de **metadatos** de una recuperación híbrida (FT00017).
 *
 * Acota la búsqueda por similitud a las publicaciones que cumplen todos los
 * campos presentes (combinados con `AND`); un campo `null` no filtra. Es un
 * **modelo de valor interno** del backend: **no** es `@Serializable` ni un DTO de
 * transporte (el DTO de la API de búsqueda llegará con FT00028), por eso va **sin
 * sufijo** (`CONSTRAINTS.md` §«Nombres y contratos»).
 *
 * [publishedFrom] y [publishedTo] son fechas **ISO-8601** (`2026-10-09`) como
 * `String`, coherentes con [es.aviferdev.datopublico.backend.ingesta.publicacion.Publication.fechaPublicacion]
 * y `PublicationRepository.listByDate`. La conversión a `LocalDate` y el
 * fail-fast de formato viven en [SearchFilterSql] (el filtro es un valor puro).
 *
 * @property publishedFrom fecha ISO-8601 **inclusiva** mínima de publicación.
 * @property publishedTo fecha ISO-8601 **inclusiva** máxima de publicación.
 * @property category categoría curada (`CategoryDto`) de la publicación.
 * @property section sección del BOE (`BoeSectionDto`) de la publicación.
 * @property organization organismo emisor exacto (valor del BOE, sensible a
 *   mayúsculas; la normalización es una decisión de la API de búsqueda).
 */
data class SearchFilter(
    val publishedFrom: String? = null,
    val publishedTo: String? = null,
    val category: CategoryDto? = null,
    val section: BoeSectionDto? = null,
    val organization: String? = null,
)
