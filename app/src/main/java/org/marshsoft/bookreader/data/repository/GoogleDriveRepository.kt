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

    companion object {
        private const val APP_FOLDER_NAME = "Book Sanctuary"
    }

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
        fileType: String,
        folderId: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val service = getDriveService(accessToken)

            // First check if file already exists
            val existingFileId = findFileId(accessToken, identifier)
            if (existingFileId != null) {
                return@withContext existingFileId
            }

            val metadata = File().apply {
                name = "$identifier.$fileType"
                appProperties = mapOf("book_identifier" to identifier)
                parents = listOf(folderId ?: "root")
            }

            val mediaContent = FileContent(if (fileType == "pdf") "application/pdf" else "application/epub+zip", localFile)
            val driveFile = service.files().create(metadata, mediaContent).execute()

            driveFile.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Finds this app's Drive folder, creating it in "My Drive" root if it doesn't exist yet. */
    suspend fun getOrCreateAppFolder(accessToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val service = getDriveService(accessToken)
            val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$APP_FOLDER_NAME' and trashed = false and 'root' in parents"
            val result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            result.files.firstOrNull()?.id ?: run {
                val folderMetadata = File().apply {
                    name = APP_FOLDER_NAME
                    mimeType = "application/vnd.google-apps.folder"
                    parents = listOf("root")
                }
                service.files().create(folderMetadata).setFields("id").execute().id
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Moves every book file this app has ever uploaded (identified by the "book_identifier"
     * appProperty, regardless of which folder it currently sits in) into [folderId]. Used once
     * to organize files that were uploaded to Drive's root before the app folder existed.
     */
    /**
     * Returns null if the listing itself failed (e.g. network/auth error) so callers can avoid
     * treating the migration as done; otherwise the count of files actually moved (0 is a valid,
     * successful result meaning nothing needed moving).
     */
    suspend fun moveAllFilesToFolder(accessToken: String, folderId: String): Int? = withContext(Dispatchers.IO) {
        var moved = 0
        try {
            val service = getDriveService(accessToken)
            var pageToken: String? = null
            do {
                // "has { key=... }" without a value isn't valid Drive query syntax (the only
                // documented form pairs a key with a specific value), so list root-level, non-
                // folder files and filter client-side by the book_identifier appProperty instead.
                val result = service.files().list()
                    .setQ("'root' in parents and trashed = false and mimeType != 'application/vnd.google-apps.folder'")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, parents, appProperties)")
                    .setPageToken(pageToken)
                    .execute()

                for (file in result.files) {
                    if (file.appProperties?.containsKey("book_identifier") != true) continue
                    val parents = file.parents ?: emptyList()
                    if (folderId in parents) continue
                    try {
                        service.files().update(file.id, null)
                            .setAddParents(folderId)
                            .setRemoveParents(parents.joinToString(","))
                            .setFields("id, parents")
                            .execute()
                        moved++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                pageToken = result.nextPageToken
            } while (pageToken != null)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
        moved
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

    suspend fun deleteFile(accessToken: String, identifier: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = getDriveService(accessToken)
            val fileId = findFileId(accessToken, identifier) ?: return@withContext true // Already gone
            service.files().delete(fileId).execute()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
