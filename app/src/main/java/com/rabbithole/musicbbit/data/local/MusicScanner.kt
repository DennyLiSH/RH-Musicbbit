package com.rabbithole.musicbbit.data.local

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.rabbithole.musicbbit.data.model.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MusicScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun scanDirectories(directories: List<String>): List<SongEntity> {
        if (directories.isEmpty()) return emptyList()

        val songs = mutableListOf<SongEntity>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.MIME_TYPE
        )

        // Build selection for directory filtering with LIKE for each directory
        val selectionBuilder = StringBuilder()
        val selectionArgs = mutableListOf<String>()

        directories.forEachIndexed { index, dir ->
            if (index > 0) selectionBuilder.append(" OR ")
            selectionBuilder.append("${MediaStore.Audio.Media.DATA} LIKE ?")
            selectionArgs.add("$dir/%")
        }

        // Also require IS_MUSIC = 1
        if (selectionBuilder.isNotEmpty()) {
            selectionBuilder.insert(0, "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND (")
            selectionBuilder.append(")")
        } else {
            selectionBuilder.append("${MediaStore.Audio.Media.IS_MUSIC} = 1")
        }

        context.contentResolver.query(
            uri,
            projection,
            selectionBuilder.toString(),
            selectionArgs.toTypedArray(),
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val albumIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val mimeType = cursor.getString(mimeTypeIndex) ?: ""
                if (!isSupportedAudioFormat(mimeType)) continue

                val albumId = cursor.getLong(albumIdIndex)
                val coverUri = getAlbumArtUri(albumId)

                songs.add(
                    SongEntity(
                        id = 0, // Room will auto-generate
                        path = cursor.getString(dataIndex) ?: "",
                        title = cursor.getString(titleIndex) ?: "",
                        artist = cursor.getString(artistIndex),
                        album = cursor.getString(albumIndex),
                        durationMs = cursor.getLong(durationIndex),
                        dateAdded = cursor.getLong(dateAddedIndex) * 1000L, // seconds to ms
                        coverUri = coverUri?.toString()
                    )
                )
            }
        }

        return songs
    }

    private fun isSupportedAudioFormat(mimeType: String): Boolean {
        return mimeType.startsWith("audio/") &&
            (mimeType.contains("mpeg") ||
                mimeType.contains("mp3") ||
                mimeType.contains("flac") ||
                mimeType.contains("wav") ||
                mimeType.contains("aac") ||
                mimeType.contains("ogg") ||
                mimeType.contains("vorbis") ||
                mimeType.contains("opus"))
    }

    private fun getAlbumArtUri(albumId: Long): Uri? {
        if (albumId <= 0) return null
        return Uri.parse("content://media/external/audio/albumart/$albumId")
    }
}
