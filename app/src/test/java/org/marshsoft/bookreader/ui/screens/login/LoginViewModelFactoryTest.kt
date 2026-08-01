package org.marshsoft.bookreader.ui.screens.login

import androidx.lifecycle.ViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.marshsoft.bookreader.data.repository.AuthRepository

class LoginViewModelFactoryTest {

    private val authRepository = mockk<AuthRepository>(relaxed = true)

    @Test
    fun create_returnsLoginViewModel() {
        every { authRepository.currentUser } returns MutableStateFlow(null)

        val factory = LoginViewModelFactory(authRepository)
        val viewModel = factory.create(LoginViewModel::class.java)

        assertNotNull(viewModel)
    }

    @Test
    fun create_unknownViewModelClass_throws() {
        val factory = LoginViewModelFactory(authRepository)

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnknownViewModel::class.java)
        }
    }

    private class UnknownViewModel : ViewModel()
}
