package es.aviferdev.datopublico.backend.ingestion.categorization

import es.aviferdev.datopublico.model.CategoryDto
import es.aviferdev.datopublico.model.BoeSectionDto
import java.text.Normalizer

/**
 * Clasificador **curado, puro y determinista** de una publicación del BOE en una
 * de las 11 categorías de [CategoryDto].
 *
 * La categoría se deduce **solo** de la [BoeSectionDto] y del texto del epígrafe
 * o, en su defecto, del título. Es una **regla curada**: no usa IA, ni red, ni
 * base de datos, ni reloj, ni estado compartido, así que es reproducible y
 * verificable en el gate.
 *
 * Invariantes:
 * - **Siempre** devuelve una [CategoryDto] (nunca `null`); si ninguna regla casa,
 *   cae en la categoría por defecto de la sección.
 * - El **epígrafe original no se modifica**: este clasificador solo lo lee; quien
 *   lo invoca lo conserva como metadato.
 * - El **orden de [RULES] es el contrato determinista**: ante varias reglas
 *   aplicables gana la primera. Se mira primero el epígrafe y luego el título.
 *
 * @see DeadlineExtractor extractor complementario del plazo de solicitud.
 */
class PublicationClassifier {

    /**
     * Clasifica una publicación en su [CategoryDto].
     *
     * @param section sección del BOE de la que procede.
     * @param heading epígrafe del sumario, o `null` si la entrada no lo trae.
     * @param title título oficial de la publicación (se usa si el epígrafe no casa).
     * @return categoría curada; la por defecto de [section] si ninguna regla casa.
     */
    fun classify(section: BoeSectionDto, heading: String?, title: String): CategoryDto =
        categoryFor(normalize(heading))
            ?: categoryFor(normalize(title))
            ?: SECTION_DEFAULTS.getValue(section)

    /** Primera regla de [RULES] cuyo vocabulario aparece en [normalizedText]. */
    private fun categoryFor(normalizedText: String): CategoryDto? =
        RULES.firstOrNull { rule -> rule.keywords.any { normalizedText.contains(it) } }?.category

    /**
     * Minúsculas, sin acentos y con espacios colapsados.
     *
     * La normalización es la misma para epígrafe y título, de modo que las reglas
     * se declaran sin acentos (`subvencion`, `informacion publica`…).
     */
    private fun normalize(text: String?): String =
        Normalizer.normalize(text.orEmpty(), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase()
            .replace(WHITESPACE, " ")
            .trim()

    /** Par vocabulario → categoría; el orden de la lista define la prioridad. */
    private data class Rule(
        val keywords: List<String>,
        val category: CategoryDto,
    )

    private companion object {
        /** Marcas diacríticas que [Normalizer] separa tras descomponer en NFD. */
        val COMBINING_MARKS = Regex("\\p{Mn}+")

        /** Espacios repetidos (incluido el espacio duro ya convertido). */
        val WHITESPACE = Regex("\\s+")

        /**
         * Reglas ordenadas por prioridad. Los términos van ya normalizados (sin
         * acentos) porque el texto se normaliza antes de comparar.
         */
        val RULES = listOf(
            Rule(listOf("convenio", "acuerdo internacional"), CategoryDto.AGREEMENTS_AND_CONVENTIONS),
            Rule(listOf("beca", "subvencion", "ayuda"), CategoryDto.SCHOLARSHIPS_GRANTS_AND_AID),
            Rule(listOf("premio"), CategoryDto.AWARDS),
            Rule(listOf("plan de estudios"), CategoryDto.EDUCATION_AND_STUDY_PLANS),
            Rule(listOf("medio ambiente", "ambiental"), CategoryDto.ENVIRONMENT),
            Rule(listOf("recurso"), CategoryDto.APPEALS_AND_RESOLUTIONS),
            Rule(
                listOf("informacion publica", "concesion"),
                CategoryDto.PUBLIC_INFORMATION_AND_CONCESSIONS,
            ),
            Rule(
                listOf("nombramiento", "cese", "designacion", "destino", "integracion", "situacion"),
                CategoryDto.APPOINTMENTS_AND_DISMISSALS,
            ),
            Rule(
                listOf(
                    "oposicion",
                    "proceso selectivo",
                    "seleccion",
                    "concurso",
                    "proveer",
                    "plaza",
                    "personal",
                    "cuerpo",
                ),
                CategoryDto.PUBLIC_EMPLOYMENT_AND_JOB_OFFERS,
            ),
        )

        /** Categoría más representativa de cada sección cuando ninguna regla casa. */
        val SECTION_DEFAULTS = mapOf(
            BoeSectionDto.I to CategoryDto.NORMS_AND_LEGISLATION,
            BoeSectionDto.II_A to CategoryDto.APPOINTMENTS_AND_DISMISSALS,
            BoeSectionDto.II_B to CategoryDto.PUBLIC_EMPLOYMENT_AND_JOB_OFFERS,
            BoeSectionDto.III to CategoryDto.OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
            BoeSectionDto.IV to CategoryDto.OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
            BoeSectionDto.V_A to CategoryDto.PUBLIC_INFORMATION_AND_CONCESSIONS,
            BoeSectionDto.V_B to CategoryDto.OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
            BoeSectionDto.V_C to CategoryDto.OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
        )
    }
}
