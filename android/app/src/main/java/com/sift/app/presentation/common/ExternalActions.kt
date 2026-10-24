package com.sift.app.presentation.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.sift.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Intents that leave the app: browser links and the share sheet. */
object ExternalActions {

    /** Opens [url] in the browser. Returns false when no app can handle it. */
    fun openUrl(context: Context, url: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        true
    } catch (notFound: ActivityNotFoundException) {
        false
    }

    /**
     * Writes [markdown] to the app cache and opens the share sheet with it as a
     * file (through FileProvider) and as plain text. No storage permission needed.
     */
    suspend fun shareMarkdown(context: Context, fileStem: String, subject: String, markdown: String) {
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
            val safeStem = fileStem.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { DEFAULT_STEM }
            val file = File(dir, "sift-$safeStem.md")
            file.writeText(markdown, Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_MARKDOWN
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, markdown)
            putExtra(Intent.EXTRA_STREAM, uri)
            // ClipData carries the read grant through the chooser, so its preview can open the file.
            clipData = ClipData.newRawUri(fileStem, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.report_share_title)))
    }

    private const val EXPORT_DIR = "exports"
    private const val DEFAULT_STEM = "report"
    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val MIME_MARKDOWN = "text/markdown"
}
