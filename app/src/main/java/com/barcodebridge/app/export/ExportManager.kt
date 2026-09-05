package com.barcodebridge.app.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes export bytes to a user-chosen [Uri] obtained via Storage Access
 * Framework (`ACTION_CREATE_DOCUMENT`) - the app never requests the legacy
 * `WRITE_EXTERNAL_STORAGE` permission - and optionally stages a copy under
 * the app's cache dir for sharing through [FileProvider] + `ACTION_SEND`.
 */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun writeToDocument(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
            ?: error("Could not open output stream for $uri")
    }

    /** Stages [bytes] under cache/exports/[filename] and returns a shareable content:// Uri. */
    suspend fun stageForSharing(filename: String, bytes: ByteArray): Uri = withContext(Dispatchers.IO) {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, filename)
        file.writeBytes(bytes)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
