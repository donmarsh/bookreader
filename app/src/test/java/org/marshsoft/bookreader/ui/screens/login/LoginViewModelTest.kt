package org.marshsoft.bookreader.ui.screens.login

import android.app.Activity
import android.content.Intent
import androidx.credentials.exceptions.GetCredentialCancellationException
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.marshsoft.bookreader.data.repository.AuthRepository
import org.marshsoft.bookreader.domain.model.User

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { authRepository.currentUser } returns MutableStateFlow(null)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun handleSignInResult_success_invokesOnSuccessAndClearsLoading() = runTest {
        val user = User(id = "1", email = "a@b.com", displayName = "A", photoUrl = null)
        coEvery { authRepository.handleSignInResult(any()) } returns Result.success(user)

        val viewModel = LoginViewModel(authRepository)
        var successCalled = false
        viewModel.handleSignInResult(mockk<Intent>()) { successCalled = true }
        advanceUntilIdle()

        assertTrue(successCalled)
        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun handleSignInResult_failure_setsErrorMessage() = runTest {
        coEvery { authRepository.handleSignInResult(any()) } returns Result.failure(Exception("boom"))

        val viewModel = LoginViewModel(authRepository)
        var successCalled = false
        viewModel.handleSignInResult(mockk<Intent>()) { successCalled = true }
        advanceUntilIdle()

        assertFalse(successCalled)
        assertFalse(viewModel.isLoading.value)
        assertEquals("boom", viewModel.error.value)
    }

    @Test
    fun signInWithCredentialManager_success_invokesOnSuccess() = runTest {
        val user = User(id = "1", email = "a@b.com", displayName = "A", photoUrl = null)
        coEvery { authRepository.signInWithCredentialManager(any()) } returns Result.success(user)

        val viewModel = LoginViewModel(authRepository)
        var successCalled = false
        var fallbackCalled = false
        viewModel.signInWithCredentialManager(
            mockk<Activity>(),
            onSuccess = { successCalled = true },
            onFallback = { fallbackCalled = true }
        )
        advanceUntilIdle()

        assertTrue(successCalled)
        assertFalse(fallbackCalled)
    }

    @Test
    fun signInWithCredentialManager_failure_invokesOnFallback() = runTest {
        coEvery { authRepository.signInWithCredentialManager(any()) } returns Result.failure(Exception("failed"))

        val viewModel = LoginViewModel(authRepository)
        var successCalled = false
        var fallbackCalled = false
        viewModel.signInWithCredentialManager(
            mockk<Activity>(),
            onSuccess = { successCalled = true },
            onFallback = { fallbackCalled = true }
        )
        advanceUntilIdle()

        assertFalse(successCalled)
        assertTrue(fallbackCalled)
    }

    @Test
    fun signInWithCredentialManager_cancellation_invokesNeitherCallback() = runTest {
        coEvery { authRepository.signInWithCredentialManager(any()) } returns
            Result.failure(GetCredentialCancellationException("cancelled"))

        val viewModel = LoginViewModel(authRepository)
        var successCalled = false
        var fallbackCalled = false
        viewModel.signInWithCredentialManager(
            mockk<Activity>(),
            onSuccess = { successCalled = true },
            onFallback = { fallbackCalled = true }
        )
        advanceUntilIdle()

        assertFalse(successCalled)
        assertFalse(fallbackCalled)
    }

    @Test
    fun onSignInError_setsErrorMessage() {
        val viewModel = LoginViewModel(authRepository)
        viewModel.onSignInError("custom error")
        assertEquals("custom error", viewModel.error.value)
    }

    @Test
    fun signOut_delegatesToAuthRepository() = runTest {
        coEvery { authRepository.signOut() } returns Unit

        val viewModel = LoginViewModel(authRepository)
        viewModel.signOut()
        advanceUntilIdle()

        coVerify { authRepository.signOut() }
    }
}
