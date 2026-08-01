package org.marshsoft.bookreader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserTest {
    @Test
    fun user_constructor_setsProperties() {
        val user = User(
            id = "1",
            email = "test@example.com",
            displayName = "Test User",
            photoUrl = "photo"
        )
        assertEquals("1", user.id)
        assertEquals("test@example.com", user.email)
        assertEquals("Test User", user.displayName)
        assertEquals("photo", user.photoUrl)
    }

    @Test
    fun user_constructor_allowsNullableFields() {
        val user = User(
            id = "1",
            email = null,
            displayName = null,
            photoUrl = null
        )
        assertNull(user.email)
        assertNull(user.displayName)
        assertNull(user.photoUrl)
    }

    @Test
    fun user_equals_comparesByValue() {
        val user1 = User(id = "1", email = "a@b.com", displayName = "A", photoUrl = null)
        val user2 = User(id = "1", email = "a@b.com", displayName = "A", photoUrl = null)
        assertEquals(user1, user2)
    }
}
