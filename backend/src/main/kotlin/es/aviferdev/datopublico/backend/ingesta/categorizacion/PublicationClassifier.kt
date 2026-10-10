package es.aviferdev.datopublico.backend.ingesta.categorizacion

import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import java.text.Normalizer

/**
 * Clasificador **curado, puro y determinista** de una publicación del BOE en una
 * de las 11 categorías de [CategoriaDto].
 *
 * La categoría se deduce **solo** de la [SeccionBoeDto] y del texto del epígrafe
 * o, en su defecto, del título. Es una **regla curada**: no usa IA, ni red, ni
 * base de datos, ni reloj, ni estado compartido, así que es reproducible y
 * verificable en el gate.
 *
 * Invariantes:
 * - **Siempre** devuelve una [CategoriaDto] (nunca `null`); si ninguna regla casa,
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
     * Clasifica una publicación en su [CategoriaDto].
     *
     * @param section sección del BOE de la que procede.
     * @param heading epígrafe del sumario, o `null` si la entrada no lo trae.
     * @param title título oficial de la publicación (se usa si el epígrafe no casa).
     * @return categoría curada; la por defecto de [section] si ninguna regla casa.
     */
    fun classify(section: SeccionBoeDto, heading: String?, title: String): CategoriaDto =
        categoryFor(normalize(heading))
            ?: categoryFor(normalize(title))
            ?: SECTION_DEFAULTS.getValue(section)

    /** Primera regla de [RULES] cuyo vocabulario aparece en [normalizedText]. */
    private fun categoryFor(normalizedText: String): CategoriaDto? =
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
        val category: CategoriaDto,
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
            Rule(listOf("convenio", "acuerdo internacional"), CategoriaDto.CONVENIOS_Y_ACUERDOS),
            Rule(listOf("beca", "subvencion", "ayuda"), CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS),
            Rule(listOf("premio"), CategoriaDto.PREMIOS),
            Rule(listOf("plan de estudios"), CategoriaDto.EDUCACION_Y_PLANES_DE_ESTUDIO),
            Rule(listOf("medio ambiente", "ambiental"), CategoriaDto.MEDIO_AMBIENTE),
            Rule(listOf("recurso"), CategoriaDto.RECURSOS_Y_RESOLUCIONES),
            Rule(
                listOf("informacion publica", "concesion"),
                CategoriaDto.INFORMACION_PUBLICA_Y_CONCESIONES,
            ),
            Rule(
                listOf("nombramiento", "cese", "designacion", "destino", "integracion", "situacion"),
                CategoriaDto.NOMBRAMIENTOS_Y_CESES,
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
                CategoriaDto.OPOSICIONES_Y_EMPLEO_PUBLICO,
            ),
        )

        /** Categoría más representativa de cada sección cuando ninguna regla casa. */
        val SECTION_DEFAULTS = mapOf(
            SeccionBoeDto.I to CategoriaDto.NORMAS_Y_LEGISLACION,
            SeccionBoeDto.II_A to CategoriaDto.NOMBRAMIENTOS_Y_CESES,
            SeccionBoeDto.II_B to CategoriaDto.OPOSICIONES_Y_EMPLEO_PUBLICO,
            SeccionBoeDto.III to CategoriaDto.OTRAS_DISPOSICIONES_Y_ANUNCIOS,
            SeccionBoeDto.IV to CategoriaDto.OTRAS_DISPOSICIONES_Y_ANUNCIOS,
            SeccionBoeDto.V_A to CategoriaDto.INFORMACION_PUBLICA_Y_CONCESIONES,
            SeccionBoeDto.V_B to CategoriaDto.OTRAS_DISPOSICIONES_Y_ANUNCIOS,
            SeccionBoeDto.V_C to CategoriaDto.OTRAS_DISPOSICIONES_Y_ANUNCIOS,
        )
    }
}
