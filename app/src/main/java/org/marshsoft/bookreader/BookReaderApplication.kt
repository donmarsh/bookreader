package org.marshsoft.bookreader

import android.app.Application
import com.google.firebase.FirebaseApp
import androidx.room.Room
import androidx.work.*
import org.marshsoft.bookreader.data.local.BookDatabase
import org.marshsoft.bookreader.data.local.SyncPreferences
import org.marshsoft.bookreader.data.repository.AuthRepository
import org.marshsoft.bookreader.data.repository.GoogleDriveRepository
import org.marshsoft.bookreader.data.repository.SyncRepository
import org.marshsoft.bookreader.data.sync.SyncWorker
import org.marshsoft.bookreader.util.BookParser
import com.google.android.gms.security.ProviderInstaller
import java.util.concurrent.TimeUnit

class BookReaderApplication : Application() {
    lateinit var database: BookDatabase
    lateinit var bookParser: BookParser
    lateinit var authRepository: AuthRepository
    lateinit var syncRepository: SyncRepository
    lateinit var syncPreferences: SyncPreferences
    lateinit var googleDriveRepository: GoogleDriveRepository

    override fun onCreate() {
        super.onCreate()
        
        // Explicitly initialize Firebase before any repositories are created
        FirebaseApp.initializeApp(this)
        
        // Initialize ProviderInstaller to ensure security provider is up to date
        // and avoid issues with GMS API calls.
        ProviderInstaller.installIfNeededAsync(this, object : ProviderInstaller.ProviderInstallListener {
            override fun onProviderInstalled() {
                // Provider is up to date
            }

            override fun onProviderInstallFailed(errorCode: Int, recoveryIntent: android.content.Intent?) {
                // Handle failure if necessary
            }
        })

        database = Room.databaseBuilder(
            this,
            BookDatabase::class.java,
            "book_reader_db"
        ).build()
        bookParser = BookParser(this)
        authRepository = AuthRepository(this)
        syncPreferences = SyncPreferences(this)
        googleDriveRepository = GoogleDriveRepository()
        syncRepository = SyncRepository(this, database.bookDao(), authRepository, syncPreferences, googleDriveRepository, bookParser)
        
        scheduleSync()
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "LibrarySync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
