package org.marshsoft.bookreader.ui.screens.login

import android.content.Intent
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

    fun signInWithGoogle(
        context: android.content.Context,
        onSuccess: () -> Unit,
        onLegacySignInRequired: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = authRepository.signInWithGoogle(context)
            _isLoading.value = false

            result.onSuccess {
                onSuccess()
            }.onFailure { e ->
                if (e is AuthRepository.LegacySignInRequiredException) {
                    onLegacySignInRequired()
                } else {
                    _error.value = e.message ?: "Sign in failed"
                }
            }
        }
    }

    fun handleLegacySignInResult(
        data: Intent?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = authRepository.handleLegacySignInResult(data)
            _isLoading.value = false

            result.onSuccess {
                onSuccess()
            }.onFailure { e ->
                _error.value = e.message ?: "Sign in failed"
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}