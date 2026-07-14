package org.marshsoft.bookreader.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTest {

    @Test
    fun reader_createRoute_returnsCorrectRoute() {
        val bookId = "test-id"
        val expected = "reader/$bookId"
        val actual = Screen.Reader.createRoute(bookId)
        assertEquals(expected, actual)
    }

    @Test
    fun login_hasCorrectRoute() {
        assertEquals("login", Screen.Login.route)
    }
}
