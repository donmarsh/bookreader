package org.marshsoft.bookreader.ui.screens.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.launch
import org.marshsoft.bookreader.BookReaderApplication
import org.marshsoft.bookreader.data.local.SyncPreferences
import org.marshsoft.bookreader.data.repository.AuthRepository

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val syncPreferences: SyncPreferences
) : ViewModel() {
    val currentUser = authRepository.currentUser
    
    var isSyncEnabled by mutableStateOf(syncPreferences.isSyncEnabled)
        private set

    var isDriveSyncEnabled by mutableStateOf(syncPreferences.isDriveSyncEnabled)
        private set

    fun toggleSync(enabled: Boolean, onRequireLogin: () -> Unit) {
        if (enabled && currentUser.value == null) {
            onRequireLogin()
            return
        }
        isSyncEnabled = enabled
        syncPreferences.isSyncEnabled = enabled
    }

    fun toggleDriveSync(context: Context, enabled: Boolean, onRequireAuth: (IntentSenderRequest) -> Unit, onRequireLogin: () -> Unit) {
        if (enabled && currentUser.value == null) {
            onRequireLogin()
            return
        }
        
        if (enabled) {
            viewModelScope.launch {
                val request = authRepository.getDriveAuthorizationRequest()
                Identity.getAuthorizationClient(context).authorize(request)
                    .addOnSuccessListener { result ->
                        if (result.hasResolution()) {
                            val intentSenderRequest = IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build()
                            onRequireAuth(intentSenderRequest)
                        } else {
                            isDriveSyncEnabled = true
                            syncPreferences.isDriveSyncEnabled = true
                        }
                    }
            }
        } else {
            isDriveSyncEnabled = false
            syncPreferences.isDriveSyncEnabled = false
        }
    }

    fun onDriveAuthResult(authorized: Boolean) {
        isDriveSyncEnabled = authorized
        syncPreferences.isDriveSyncEnabled = authorized
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            isSyncEnabled = false
            syncPreferences.isSyncEnabled = false
            isDriveSyncEnabled = false
            syncPreferences.isDriveSyncEnabled = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as BookReaderApplication
    val viewModel: SettingsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(app.authRepository, app.syncPreferences) as T
            }
        }
    )

    val user by viewModel.currentUser.collectAsState()
    
    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onDriveAuthResult(true)
        } else {
            viewModel.onDriveAuthResult(false)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val userValue = user
            if (userValue != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(userValue.displayName ?: "Unknown User", style = MaterialTheme.typography.bodyLarge)
                            Text(userValue.email ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { viewModel.signOut() }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                        }
                    }
                }
            } else {
                Button(
                    onClick = onLoginClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign in with Google")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Synchronization",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            ListItem(
                headlineContent = { Text("Sync Reading Progress") },
                supportingContent = { Text("Keep your reading position synced across devices via Firestore") },
                leadingContent = { Icon(Icons.Default.Cloud, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = viewModel.isSyncEnabled,
                        onCheckedChange = { viewModel.toggleSync(it, onLoginClick) }
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ListItem(
                headlineContent = { Text("Google Drive Backup") },
                supportingContent = { Text("Backup your book files to your Google Drive") },
                leadingContent = { Icon(Icons.Default.Sync, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = viewModel.isDriveSyncEnabled,
                        onCheckedChange = { viewModel.toggleDriveSync(context, it, { req -> driveAuthLauncher.launch(req) }, onLoginClick) }
                    )
                }
            )
            
            if (viewModel.isSyncEnabled) {
                TextButton(
                    onClick = { 
                        viewModel.viewModelScope.launch {
                            app.syncRepository.syncAll(context)
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sync Now")
                }
            }
        }
    }
}
