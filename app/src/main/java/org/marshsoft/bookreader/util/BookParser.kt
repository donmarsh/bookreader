package org.marshsoft.bookreader.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.CoverService
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.io.FileOutputStream

class BookParser(private val context: Context) {
    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val pdfFactory = PdfiumDocumentFactory(context)
    private val publicationParser = DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory = pdfFactory)
    private val publicationOpener = PublicationOpener(publicationParser)

    suspend fun parsePublication(file: File): Publication? = withContext(Dispatchers.IO) {
        try {
            val asset = assetRetriever.retrieve(file).getOrNull() ?: return@withContext null
            publicationOpener.open(asset, allowUserInteraction = false).getOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getCover(publication: Publication): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // 1. Try standard CoverService (handles standard EPUB cover metadata)
            val cover = publication.findService(CoverService::class)?.cover()
            if (cover != null) return@withContext cover

            // 2. Fallback: Manually search for items that look like covers in the manifest
            // We search both resources and links for common naming patterns or rel="cover"
            val allLinks = publication.resources + publication.links
            val coverLink = allLinks.firstOrNull { link ->
                link.rels.contains("cover") || 
                link.href.toString().contains("cover", ignoreCase = true) ||
                link.href.toString().contains("folder", ignoreCase = true) ||
                link.href.toString().contains("front", ignoreCase = true) ||
                link.href.toString().contains("thumb", ignoreCase = true)
            }

            if (coverLink != null) {
                // Use the publication's fetcher to get the raw bytes of the potential cover
                val resource = publication.get(coverLink)
                val bytes = resource?.read()?.getOrNull()
                if (bytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) return@withContext bitmap
                }
            }

            // 3. Last Resort: Try the first image resource we can find in the entire package
            val firstImageLink = allLinks.firstOrNull { it.mediaType?.type == "image" }
            if (firstImageLink != null) {
                val bytes = publication.get(firstImageLink)?.read()?.getOrNull()
                if (bytes != null) {
                    return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }

    fun saveFileToInternal(uri: Uri, fileName: String): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val file = File(context.filesDir, fileName)
            val outputStream = FileOutputStream(file)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveBitmapToInternal(bitmap: Bitmap, fileName: String): String? {
        return try {
            val file = File(context.filesDir, fileName)
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
