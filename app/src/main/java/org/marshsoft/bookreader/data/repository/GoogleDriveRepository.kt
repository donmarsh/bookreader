package org.marshsoft.bookreader.data.repository

import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.OutputStream

class GoogleDriveRepository {

    private fun getDriveService(accessToken: String): Drive {
        val requestInitializer = HttpRequestInitializer { request ->
            request.headers.authorization = "Bearer $accessToken"
        }

        return Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            requestInitializer,
        ).setApplicationName("Book Sanctuary")
            .build()
    }

    suspend fun uploadFile(
        accessToken: String,
        localFile: java.io.File,
        identifier: String,
        fileType: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val service = getDriveService(accessToken)
            
            // First check if file already exists
            val existingFileId = findFileId(accessToken, identifier)
            
            val metadata = File().apply {
                name = "$identifier.$fileType"
                appProperties = mapOf("book_identifier" to identifier)
                parents = listOf("root")
            }
            
            val mediaContent = FileContent(if (fileType == "pdf") "application/pdf" else "application/epub+zip", localFile)
            
            val driveFile = if (existingFileId != null) {
                service.files().update(existingFileId, metadata, mediaContent).execute()
            } else {
                service.files().create(metadata, mediaContent).execute()
            }
            
            driveFile.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun downloadFile(
        accessToken: String,
        identifier: String,
        destination: java.io.File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = getDriveService(accessToken)
            val fileId = findFileId(accessToken, identifier) ?: return@withContext false
            
            val outputStream: OutputStream = FileOutputStream(destination)
            service.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            outputStream.flush()
            outputStream.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun findFileId(accessToken: String, identifier: String): String? = withContext(Dispatchers.IO) {
        try {
            val service = getDriveService(accessToken)
            val query = "appProperties has { key='book_identifier' and value='${identifier}' } and trashed = false"
            val result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()
            
            result.files.firstOrNull()?.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
