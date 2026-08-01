package org.marshsoft.bookreader.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.collect
import org.marshsoft.bookreader.BookReaderApplication

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as BookReaderApplication
        val syncRepository = app.syncRepository
        val authRepository = app.authRepository

        if (authRepository.currentUser.value == null) {
            return Result.success()
        }

        return try {
            syncRepository.syncAll(isManual = false).collect()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
