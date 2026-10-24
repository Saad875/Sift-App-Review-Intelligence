package com.sift.app.data.csv

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.sift.app.domain.error.AppError
import com.sift.app.domain.model.CsvDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Reads a CSV picked with ACTION_OPEN_DOCUMENT. No storage permission is needed. */
class CsvDocumentReader(private val resolver: ContentResolver) {

    /** File bytes plus a display name; throws [AppError.FileUnreadable] on failure or if too large. */
    suspend fun read(document: CsvDocument): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(document.uri)
        val name = document.displayName ?: queryName(uri) ?: DEFAULT_NAME
        try {
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > MAX_BYTES) throw AppError.FileUnreadable()
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } ?: throw AppError.FileUnreadable()
            bytes to name
        } catch (io: IOException) {
            throw AppError.FileUnreadable(io)
        } catch (security: SecurityException) {
            throw AppError.FileUnreadable(security)
        }
    }

    private fun queryName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
        const val BUFFER_SIZE = 16 * 1024
        const val DEFAULT_NAME = "reviews.csv"
    }
}
