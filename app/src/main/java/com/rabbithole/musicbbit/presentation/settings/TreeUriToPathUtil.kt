package com.rabbithole.musicbbit.presentation.settings

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/**
 * Converts a SAF tree URI to a physical file system path.
 *
 * Only handles the "primary" external storage volume. Returns `null` for
 * secondary volumes, non-tree URIs, or any URI that does not represent
 * primary storage.
 */
fun Context.getPathFromTreeUri(uri: Uri): String? {
    if (!DocumentsContract.isTreeUri(uri)) return null

    val docId = DocumentsContract.getTreeDocumentId(uri)
    val split = docId.split(":", limit = 2)

    return when {
        split.size == 2 && split[0].equals("primary", ignoreCase = true) -> {
            @Suppress("DEPRECATION") // SAF tree URI -> physical path requires legacy API
            File(Environment.getExternalStorageDirectory(), split[1]).canonicalPath
        }
        else -> null
    }
}
