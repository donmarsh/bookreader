package org.marshsoft.bookreader.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import nl.siegmann.epublib.epub.EpubReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import nl.siegmann.epublib.domain.Book as EpubBook

class BookParser(private val context: Context) {

    fun parseEpub(uri: Uri): EpubBook? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            EpubReader().readEpub(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getEpubCover(epubBook: EpubBook): Bitmap? {
        return try {
            val coverImage = epubBook.coverImage ?: return null
            val inputStream = coverImage.inputStream
            android.graphics.BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getPdfMetadata(uri: Uri): Pair<String, String>? {
        val fileName = getFileName(uri) ?: "Unknown"
        return Pair(fileName, "Unknown Author")
    }

    private fun getFileName(uri: Uri): String? {
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

    fun getPdfCover(uri: Uri): Bitmap? {
        return try {
            val fileDescriptor: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
            if (fileDescriptor != null) {
                val renderer = PdfRenderer(fileDescriptor)
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    renderer.close()
                    return bitmap
                }
                renderer.close()
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
