package org.marshsoft.bookreader.data.repository

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import org.marshsoft.bookreader.domain.model.User
import java.security.SecureRandom

class AuthRepository(private val appContext: Context) {
    companion object {
        private const val WEB_CLIENT_ID = "993966114933-u4e9ra4vmamc639jufjli5q3k2qlkq7l.apps.googleusercontent.com"
        private const val TAG = "Auth"
    }

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
            .requestIdToken(WEB_CLIENT_ID)
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

    suspend fun signInWithGoogle(context: Context): Result<User> {
        val activity = context.findActivity() ?: return Result.failure(Exception("Activity context required for Credential Manager"))
        val nonce = generateSecureRandomNonce()

        return try {
            // 1. Try with authorized accounts first (FilterByAuthorizedAccounts = true)
            // This is the cleanest UX for returning users (One Tap)
            val authorizedOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(true)
                .setNonce(nonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(authorizedOption)
                .build()

            try {
                val result = credentialManager.getCredential(activity, request)
                return handleCredential(result.credential)
            } catch (e: NoCredentialException) {
                Log.d(TAG, "No authorized credentials found, trying account selector: ${e.message}")
            } catch (e: GetCredentialException) {
                // If it's a cancellation or other error, we might still want to try the selector
                // unless it was an explicit user cancellation that should stop the flow.
                Log.d(TAG, "First attempt failed: ${e.message}")
            }

            // 2. Try with account selector if no authorized ones found
            // We use GetSignInWithGoogleOption as it's specifically designed for the "Sign in with Google" button flow
            val signInOption = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID)
                .setNonce(nonce)
                .build()

            // Also include GetGoogleIdOption with filter=false as an alternative
            val allAccountsOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .setNonce(nonce)
                .build()

            val secondRequest = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .addCredentialOption(allAccountsOption)
                .build()

            val secondResult = credentialManager.getCredential(activity, secondRequest)
            return handleCredential(secondResult.credential)

        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager failed: ${e.message}", e)
            Result.failure(LegacySignInRequiredException())
        } catch (e: Exception) {
            Log.e(TAG, "Sign in error: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    private suspend fun handleCredential(credential: androidx.credentials.Credential): Result<User> {
        return try {
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
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "Failed to parse Google ID token", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling credential", e)
            Result.failure(e)
        }
    }

    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }

    private fun generateSecureRandomNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return android.util.Base64.encodeToString(randomBytes, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
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