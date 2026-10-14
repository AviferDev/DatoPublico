package es.aviferdev.datopublico.app

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests de las rutas del grafo de navegación: los literales de las rutas, la
 * clave del argumento y la construcción de la ruta de detalle (incluida la
 * codificación de caracteres reservados y no ASCII).
 *
 * Son funciones puras; corren en `:composeApp:jvmTest` sin red ni BD.
 */
class AppRoutesTest {
    @Test
    fun feedRouteIsStable() {
        assertEquals("feed", AppRoutes.FEED)
    }

    @Test
    fun chatRouteIsStable() {
        assertEquals("chat", AppRoutes.CHAT)
    }

    @Test
    fun searchRouteIsStable() {
        assertEquals("search", AppRoutes.SEARCH)
    }

    @Test
    fun detailPatternDeclaresArgument() {
        assertEquals("detail/{publicationId}", AppRoutes.DETAIL)
    }

    @Test
    fun argumentKeyIsPublicationId() {
        assertEquals("publicationId", AppRoutes.ARG_PUBLICATION_ID)
    }

    @Test
    fun detailKeepsBoeIdentifierUnchanged() {
        assertEquals("detail/BOE-A-2026-21001", AppRoutes.detail("BOE-A-2026-21001"))
    }

    @Test
    fun detailEncodesReservedCharacters() {
        assertEquals("detail/a%2Fb", AppRoutes.detail("a/b"))
    }

    @Test
    fun detailEncodesNonAsciiCharactersAsUtf8() {
        assertEquals("detail/caf%C3%A9", AppRoutes.detail("café"))
    }
}
