package com.rabbithole.musicbbit.data.local

import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for [MusicScanner]'s pure selection-building and format-whitelist logic,
 * testable since the MediaStore query moved behind [MediaStorePort].
 */
class MusicScannerTest {

    @Test
    fun `selection requires IS_MUSIC and filters each directory with LIKE`() {
        val (selection, args) = MusicScanner.buildAudioSelection(listOf("/music/a", "/music/b"))

        assertEquals(
            "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND (" +
                "${MediaStore.Audio.Media.DATA} LIKE ? OR ${MediaStore.Audio.Media.DATA} LIKE ?)",
            selection
        )
        assertEquals(listOf("/music/a/%", "/music/b/%"), args)
    }

    @Test
    fun `selection with no directories is just IS_MUSIC`() {
        val (selection, args) = MusicScanner.buildAudioSelection(emptyList())

        assertEquals("${MediaStore.Audio.Media.IS_MUSIC} = 1", selection)
        assertTrue(args.isEmpty())
    }

    @Test
    fun `supported audio formats pass the whitelist`() {
        listOf(
            "audio/mpeg", "audio/mp3", "audio/flac", "audio/x-flac",
            "audio/wav", "audio/aac", "audio/ogg", "audio/vorbis", "audio/opus",
        ).forEach { mime ->
            assertTrue("expected $mime to be supported", MusicScanner.isSupportedAudioFormat(mime))
        }
    }

    @Test
    fun `unsupported formats fail the whitelist`() {
        listOf("video/mp4", "audio/amr", "audio/midi", "image/png", "").forEach { mime ->
            assertFalse("expected $mime to be rejected", MusicScanner.isSupportedAudioFormat(mime))
        }
    }
}
