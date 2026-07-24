package org.marshsoft.bookreader.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.ApiException
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

class AuthRepository(private val appContext: Context) {
    private val auth: FirebaseAuth = Firebase.auth
    private val credentialManager = CredentialManager.create(appContext)

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
        // ...
    }

    fun getDriveAuthorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(Scopes.DRIVE_FILE)))
            .build()
    }

    suspend fun getAccessToken(activity: Context): String? {
        return try {
            val authorizationClient = Identity.getAuthorizationClient(activity)
            val request = getDriveAuthorizationRequest()
            val result = authorizationClient.authorize(request).await()
            result.accessToken
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ===== LEGACY FALLBACK =====
    private val legacySignInClient by lazy {
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestIdToken("993966114933-u4e9ra4vmamc639jufjli5q3k2qlkq7l.apps.googleusercontent.com")
            .requestEmail()
            .build()
        com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(appContext, gso)
    }

    fun getLegacySignInIntent() = legacySignInClient.signInIntent

    suspend fun handleLegacySignInResult(data: android.content.Intent?): Result<User> {
        return try {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken ?: throw Exception("No ID token")
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user?.toDomain() ?: throw Exception("Sign in failed")
            Result.success(user)
        } catch (e: Exception) {
            Log.e("Auth", "Legacy sign-in failed", e)
            Result.failure(e)
        }
    }
    // ===========================

    suspend fun signInWithGoogle(activity: Context): Result<User> {
        return try {
            // Try Credential Manager first
            val webClientId = "993966114933-u4e9ra4vmamc639jufjli5q3k2qlkq7l.apps.googleusercontent.com"

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false) // Force account picker
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential

            val googleIdTokenCredential = when {
                credential is GoogleIdTokenCredential -> credential
                credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                    GoogleIdTokenCredential.createFrom(credential.data)
                }
                else -> null
            }

            if (googleIdTokenCredential != null) {
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user?.toDomain() ?: throw Exception("Sign in failed")
                Result.success(user)
            } else {
                Result.failure(Exception("Unsupported credential type: ${credential::class.java.name}"))
            }
        } catch (e: GetCredentialException) {
            Log.w("Auth", "Credential Manager failed, falling back to legacy", e)
            // Return a special failure that tells the UI to use legacy sign-in
            Result.failure(LegacySignInRequiredException())
        } catch (e: Exception) {
            Log.e("Auth", "type=${e.localizedMessage} message=${e.message}", e)
            Result.failure(e)
        }
    }

    // Exception to signal UI to use legacy flow
    class LegacySignInRequiredException : Exception("Use legacy Google Sign-In")

    suspend fun signOut() {
        auth.signOut()
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w("Auth", "Failed to clear credential state", e)
        }
        try {
            legacySignInClient.signOut().await()
        } catch (e: Exception) {
            Log.w("Auth", "Failed to sign out legacy client", e)
        }
    }

    private fun com.google.firebase.auth.FirebaseUser.toDomain() = User(
        id = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString(),
    )
}