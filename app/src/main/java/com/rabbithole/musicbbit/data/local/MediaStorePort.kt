package com.rabbithole.musicbbit.data.local

import android.content.Context
import android.database.Cursor
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seam over [android.content.ContentResolver] media queries. Lets [MusicScanner]'s
 * selection building and row mapping be tested without a real MediaStore — tests bind a
 * fake returning canned cursors, production wraps the system resolver.
 */
interface MediaStorePort {

    fun query(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>,
        sortOrder: String?,
    ): Cursor?
}

@Singleton
class ContentResolverMediaStorePort @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : MediaStorePort {

    override fun query(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>,
        sortOrder: String?,
    ): Cursor? = context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
}
