package es.aviferdev.datopublico.backend.ingesta.sumario

import es.aviferdev.datopublico.model.SeccionBoeDto

/**
 * Entrada del sumario del BOE ya traducida al modelo **interno** del backend.
 *
 * No es un DTO de transporte: no es serializable ni se expone por HTTP. Es la
 * forma estable que consumirán el parser de publicaciones (FT00007), la
 * persistencia (FT00008) y el job de ingesta (FT00009).
 *
 * [fechaPublicacion] es una fecha ISO-8601 (`2026-10-09`) como `String`,
 * coherente con `PublicacionDto`. [organismo] y [epigrafe] son opcionales porque
 * la estructura varía entre secciones; [control] falta en las entradas que el BOE
 * publica directamente bajo un departamento (sin epígrafe).
 *
 * [urlOficial] es el enlace HTML (`txt.php?id=…`) o, si no existe, la URL del PDF;
 * [urlXml] y [urlPdf] son opcionales según la sección.
 */
data class EntradaSumario(
    val identificador: String,
    val control: String?,
    val titulo: String,
    val fechaPublicacion: String,
    val seccion: SeccionBoeDto,
    val organismo: String?,
    val epigrafe: String?,
    val urlOficial: String,
    val urlXml: String?,
    val urlPdf: String?,
)
