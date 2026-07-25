package org.marshsoft.bookreader.data.repository

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import org.marshsoft.bookreader.domain.model.User

class AuthRepository(private val appContext: Context) {
    companion object {
        private const val WEB_CLIENT_ID = "993966114933-u4e9ra4vmamc639jufjli5q3k2qlkq7l.apps.googleusercontent.com"
        private const val TAG = "Auth"
    }

    private val auth: FirebaseAuth = Firebase.auth

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

    // ===== GOOGLE SIGN IN (LEGACY) =====
    private val googleSignInClient by lazy {
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(appContext, gso)
    }

    fun getSignInIntent() = googleSignInClient.signInIntent

    suspend fun handleSignInResult(data: android.content.Intent?): Result<User> {
        return try {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken ?: throw Exception("No ID token")
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user?.toDomain() ?: throw Exception("Sign in failed")
            Result.success(user)
        } catch (e: ApiException) {
            Log.e(TAG, "Sign-in ApiException: status code = ${e.statusCode}, message = ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed", e)
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        auth.signOut()
        try {
            googleSignInClient.signOut().await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sign out Google client", e)
        }
    }

    private fun com.google.firebase.auth.FirebaseUser.toDomain() = User(
        id = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString(),
    )
}
