package org.marshsoft.bookreader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.marshsoft.bookreader.data.repository.AuthRepository
import org.marshsoft.bookreader.domain.model.User

class MainViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    val currentUser: StateFlow<User?> = authRepository.currentUser

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
