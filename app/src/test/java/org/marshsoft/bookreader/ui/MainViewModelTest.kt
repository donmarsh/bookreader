package org.marshsoft.bookreader.ui

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.marshsoft.bookreader.data.repository.AuthRepository
import org.marshsoft.bookreader.domain.model.User

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun currentUser_reflectsAuthRepositoryState() {
        val userFlow = MutableStateFlow<User?>(null)
        every { authRepository.currentUser } returns userFlow

        val viewModel = MainViewModel(authRepository)
        assertNull(viewModel.currentUser.value)

        val user = User(id = "1", email = "a@b.com", displayName = "A", photoUrl = null)
        userFlow.value = user
        assertEquals(user, viewModel.currentUser.value)
    }

    @Test
    fun signOut_delegatesToAuthRepository() = runTest {
        every { authRepository.currentUser } returns MutableStateFlow(null)
        coEvery { authRepository.signOut() } returns Unit

        val viewModel = MainViewModel(authRepository)
        viewModel.signOut()
        advanceUntilIdle()

        coVerify { authRepository.signOut() }
    }
}
