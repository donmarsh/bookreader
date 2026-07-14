package org.marshsoft.bookreader.data.repository

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import org.marshsoft.bookreader.domain.model.User

class AuthRepository(private val context: Context) {
    private val auth: FirebaseAuth = Firebase.auth
    private val credentialManager = CredentialManager.create(context)
    private val authorizationClient = Identity.getAuthorizationClient(context)

    private val _currentUser = MutableStateFlow(auth.currentUser?.toDomain())
    val currentUser: StateFlow<User?> = _currentUser

    private val _isDriveAuthorized = MutableStateFlow(false)
    val isDriveAuthorized: StateFlow<Boolean> = _isDriveAuthorized

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser?.toDomain()
            checkDriveAuthorization()
        }
    }

    private fun checkDriveAuthorization() {
        val driveScope = Scope(Scopes.DRIVE_FILE)
        // Note: hasCapabilities might not be available on all Play Services versions
        // We'll also try to just authorize and see if it has resolution
    }

    fun getDriveAuthorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(Scopes.DRIVE_FILE)))
            .build()
    }

    suspend fun getAccessToken(): String? {
        return try {
            val request = getDriveAuthorizationRequest()
            val result = authorizationClient.authorize(request).await()
            result.accessToken
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun signInWithGoogle(): Result<User> {
        return try {
            val webClientId = "993966114933-u4e9ra4vmamc639jufjli5q3k2qlkq7l.apps.googleusercontent.com"
            
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts = false)
                .setServerClientId(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is GoogleIdTokenCredential) {
                val firebaseCredential = GoogleAuthProvider.getCredential(credential.idToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user?.toDomain() ?: throw Exception("Sign in failed")
                Result.success(user)
            } else {
                Result.failure(Exception("Unsupported credential type"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        auth.signOut()
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    private fun com.google.firebase.auth.FirebaseUser.toDomain() = User(
        id = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString(),
    )

    // TODO: Implement Google Drive Authorization for file backup
}
