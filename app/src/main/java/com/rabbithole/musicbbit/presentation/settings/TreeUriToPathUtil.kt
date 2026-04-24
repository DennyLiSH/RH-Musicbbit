package com.rabbithole.musicbbit.presentation.settings

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

fun Context.getPathFromTreeUri(uri: Uri): String? {
    if (!DocumentsContract.isTreeUri(uri)) return null

    val docId = DocumentsContract.getTreeDocumentId(uri)
    val split = docId.split(":", limit = 2)

    return when {
        split.size >= 2 && split[0].equals("primary", ignoreCase = true) -> {
            Environment.getExternalStorageDirectory().path + "/" + split[1]
        }
        else -> null
    }
}
