package com.rabbithole.musicbbit.presentation.settings

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

sealed interface TreeUriPathResult {
    data class Success(val path: String) : TreeUriPathResult
    data object UnsupportedStorage : TreeUriPathResult
    data object ParseFailed : TreeUriPathResult
}

fun Context.getPathFromTreeUri(uri: Uri): TreeUriPathResult {
    if (!DocumentsContract.isTreeUri(uri)) return TreeUriPathResult.ParseFailed

    val docId = try {
        DocumentsContract.getTreeDocumentId(uri)
    } catch (e: IllegalArgumentException) {
        return TreeUriPathResult.ParseFailed
    }
    val split = docId.split(":", limit = 2)

    return when {
        split.size != 2 -> TreeUriPathResult.ParseFailed
        split[0].equals("primary", ignoreCase = true) -> {
            @Suppress("DEPRECATION")
            TreeUriPathResult.Success(
                File(Environment.getExternalStorageDirectory(), split[1]).canonicalPath
            )
        }
        else -> TreeUriPathResult.UnsupportedStorage
    }
}
