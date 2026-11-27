package com.ogmediaapps.eventbox.view

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.ogmediaapps.eventbox.R
import com.ogmediaapps.eventbox.filter.OGEventBoxExport
import com.ogmediaapps.eventbox.model.OGEventBoxDebugEvent
import com.ogmediaapps.eventbox.safely
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Copying one event to the clipboard and sharing the visible log, split out of [OGEventBoxView]. */
internal class OGEventBoxShare(private val context: Context) {

    fun copy(event: OGEventBoxDebugEvent) {
        safely {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            clipboard.setPrimaryClip(
                ClipData.newPlainText(context.getString(R.string.og_event_box_copy_label), OGEventBoxExport.eventJson(event))
            )
            // Android 13+ shows its own clipboard confirmation.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context.applicationContext, R.string.og_event_box_copied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Shares [visible] as text, or as a JSON file when it is too long. [beforeSend] runs once there is something to share. */
    fun shareLog(visible: List<OGEventBoxDebugEvent>, beforeSend: () -> Unit) {
        safely {
            if (visible.isEmpty()) {
                Toast.makeText(context.applicationContext, R.string.og_event_box_share_empty, Toast.LENGTH_SHORT).show()
                return
            }
            beforeSend()
            val json = OGEventBoxExport.logJson(visible)
            val subject = context.resources.getQuantityString(R.plurals.og_event_box_share_subject, visible.size, visible.size)
            // Long sessions don't fit in an intent extra (the binder limit is ~1 MB for the whole
            // transaction), so they go out as a JSON file from the cache instead.
            val fileUri = if (json.length > MAX_SHARE_TEXT_CHARS) writeExportFile(json) else null
            val send = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_SUBJECT, subject)
                if (fileUri != null) {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    clipData = ClipData.newRawUri(subject, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, json)
                }
            }
            val chooser = Intent.createChooser(send, context.getString(R.string.og_event_box_share_title))
            // The overlay window has an application context, which needs a new task.
            if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    /**
     * Writes [json] to `cache/og_event_box/` (replacing the previous export) and returns a
     * content URI for it, or null when that fails so the caller falls back to plain text.
     */
    private fun writeExportFile(json: String): android.net.Uri? = runCatching {
        val dir = java.io.File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = java.io.File(dir, "event-box-$stamp.json")
        file.writeText(json, Charsets.UTF_8)
        androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}$EXPORT_AUTHORITY_SUFFIX", file)
    }.getOrNull()

    private companion object {
        const val MAX_SHARE_TEXT_CHARS = 100_000
        const val EXPORT_DIR = "og_event_box"
        const val EXPORT_AUTHORITY_SUFFIX = ".ogeventbox.files"
    }
}
