package es.aviferdev.datopublico.design.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import es.aviferdev.datopublico.design.theme.DatoPublicoTheme
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory
import es.aviferdev.datopublico.resources.Res
import es.aviferdev.datopublico.resources.category_agreements_and_conventions
import es.aviferdev.datopublico.resources.category_appeals_and_resolutions
import es.aviferdev.datopublico.resources.category_appointments_and_dismissals
import es.aviferdev.datopublico.resources.category_awards
import es.aviferdev.datopublico.resources.category_education_and_study_plans
import es.aviferdev.datopublico.resources.category_environment
import es.aviferdev.datopublico.resources.category_grants_and_subsidies
import es.aviferdev.datopublico.resources.category_normative_regulation
import es.aviferdev.datopublico.resources.category_other_provisions_and_announcements
import es.aviferdev.datopublico.resources.category_public_employment_and_exams
import es.aviferdev.datopublico.resources.category_public_information_and_concessions
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Tono visual de una etiqueta de categoría según `DESIGN.md`: sólo existen tres
 * tonos (no hay un color por categoría) y se resuelven **siempre** desde el
 * esquema de color del tema, nunca con literales.
 */
private enum class CategoryTone {
    /** Lo normativo → `primary`. */
    NORMATIVE,

    /** Lo informativo/administrativo → `secondary`. */
    INFORMATIVE,

    /** Lo que implica plazo o atención (convocatorias y ayudas) → `tertiary`. */
    URGENT;

    /**
     * Color de fondo de la pastilla para este tono.
     *
     * @param scheme esquema de color activo del tema.
     */
    fun background(scheme: ColorScheme): Color = when (this) {
        NORMATIVE -> scheme.primary
        INFORMATIVE -> scheme.secondary
        URGENT -> scheme.tertiary
    }

    /**
     * Color del texto sobre [background], elegido con contraste **AA**.
     *
     * @param scheme esquema de color activo del tema.
     */
    fun content(scheme: ColorScheme): Color = when (this) {
        NORMATIVE -> scheme.onPrimary
        INFORMATIVE -> scheme.onSecondary
        URGENT -> scheme.onTertiary
    }
}

/**
 * Etiqueta sobria de categoría: pastilla con el texto localizado y el tono de
 * `DESIGN.md`.
 *
 * La categoría se recibe como valor de **dominio**; el texto sale de recursos i18n
 * y el color únicamente de `MaterialTheme.colorScheme`.
 *
 * @param category categoría curada de la publicación.
 * @param modifier modificador de layout del llamante.
 */
@Composable
fun CategoryLabel(category: PublicationCategory, modifier: Modifier = Modifier) {
    val tone = category.tone()
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = tone.background(MaterialTheme.colorScheme),
        contentColor = tone.content(MaterialTheme.colorScheme),
    ) {
        Text(
            text = stringResource(category.label()),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * Tono de la categoría según `DESIGN.md`.
 *
 * `DESIGN.md` asigna `secondary` a «Empleo público» y el tono de acento a «Becas y
 * subvenciones», pero su regla de agrupación es la operativa para las etiquetas
 * («`accent` para lo que implica plazo o atención (convocatorias y ayudas)»). Se
 * aplica la agrupación: las **dos** categorías que exigen plazo (empleo público y
 * becas/subvenciones) comparten el tono de atención.
 */
private fun PublicationCategory.tone(): CategoryTone = when (this) {
    PublicationCategory.NORMATIVE_REGULATION -> CategoryTone.NORMATIVE
    PublicationCategory.PUBLIC_EMPLOYMENT_AND_EXAMS,
    PublicationCategory.GRANTS_AND_SUBSIDIES,
    -> CategoryTone.URGENT
    PublicationCategory.APPOINTMENTS_AND_DISMISSALS,
    PublicationCategory.AWARDS,
    PublicationCategory.AGREEMENTS_AND_CONVENTIONS,
    PublicationCategory.EDUCATION_AND_STUDY_PLANS,
    PublicationCategory.ENVIRONMENT,
    PublicationCategory.APPEALS_AND_RESOLUTIONS,
    PublicationCategory.PUBLIC_INFORMATION_AND_CONCESSIONS,
    PublicationCategory.OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
    -> CategoryTone.INFORMATIVE
}

/** Texto localizado de la categoría. */
private fun PublicationCategory.label(): StringResource = when (this) {
    PublicationCategory.NORMATIVE_REGULATION -> Res.string.category_normative_regulation
    PublicationCategory.APPOINTMENTS_AND_DISMISSALS -> Res.string.category_appointments_and_dismissals
    PublicationCategory.PUBLIC_EMPLOYMENT_AND_EXAMS -> Res.string.category_public_employment_and_exams
    PublicationCategory.GRANTS_AND_SUBSIDIES -> Res.string.category_grants_and_subsidies
    PublicationCategory.AWARDS -> Res.string.category_awards
    PublicationCategory.AGREEMENTS_AND_CONVENTIONS -> Res.string.category_agreements_and_conventions
    PublicationCategory.EDUCATION_AND_STUDY_PLANS -> Res.string.category_education_and_study_plans
    PublicationCategory.ENVIRONMENT -> Res.string.category_environment
    PublicationCategory.APPEALS_AND_RESOLUTIONS -> Res.string.category_appeals_and_resolutions
    PublicationCategory.PUBLIC_INFORMATION_AND_CONCESSIONS ->
        Res.string.category_public_information_and_concessions
    PublicationCategory.OTHER_PROVISIONS_AND_ANNOUNCEMENTS ->
        Res.string.category_other_provisions_and_announcements
}

/** Previsualización del tono normativo (`primary`). */
@Preview
@Composable
private fun CategoryLabelNormativePreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CategoryLabel(
                category = PublicationCategory.NORMATIVE_REGULATION,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización del tono informativo/administrativo (`secondary`). */
@Preview
@Composable
private fun CategoryLabelInformativePreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CategoryLabel(
                category = PublicationCategory.APPOINTMENTS_AND_DISMISSALS,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Previsualización del tono de plazo/atención (`tertiary`). */
@Preview
@Composable
private fun CategoryLabelUrgentPreview() {
    DatoPublicoTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CategoryLabel(
                category = PublicationCategory.GRANTS_AND_SUBSIDIES,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
