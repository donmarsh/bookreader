package org.marshsoft.bookreader.ui.screens.login

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.marshsoft.bookreader.data.repository.AuthRepository

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    val currentUser = authRepository.currentUser

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun handleSignInResult(
        data: Intent?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = authRepository.handleSignInResult(data)
            _isLoading.value = false

            result.onSuccess {
                onSuccess()
            }.onFailure { e ->
                _error.value = e.message ?: "Sign in failed"
            }
        }
    }

    fun signInWithCredentialManager(
        activity: Activity,
        onSuccess: () -> Unit,
        onFallback: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = authRepository.signInWithCredentialManager(activity)
            _isLoading.value = false

            result.onSuccess {
                onSuccess()
            }.onFailure { e ->
                if (e is GetCredentialCancellationException) {
                    // User canceled, do nothing
                } else {
                    Log.e("LoginViewModel", "Credential Manager failed: ${e.message}", e)
                    onFallback()
                }
            }
        }
    }

    fun onSignInError(message: String) {
        _error.value = message
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
