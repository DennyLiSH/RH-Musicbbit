package com.rabbithole.musicbbit.data.repository

import com.rabbithole.musicbbit.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SongSorterTest {

    private val sorter = SongSorter()

    private fun song(
        id: Long,
        title: String,
        path: String = "/storage/emulated/0/Music/song-$id.mp3",
        artist: String? = null,
        durationMs: Long = 1000L,
        dateAdded: Long = 0L,
        album: String? = null,
        coverUri: String? = null
    ) = Song(
        id = id, path = path, title = title, artist = artist, album = album,
        durationMs = durationMs, dateAdded = dateAdded, coverUri = coverUri
    )

    private fun titles(songs: List<Song>): List<String> = songs.map { it.title }

    @Test
    fun `sorts ASCII titles in ascending order`() {
        val input = listOf(
            song(1, "Cherry"),
            song(2, "Apple"),
            song(3, "Banana")
        )

        val sorted = sorter.sort(input)

        assertEquals(listOf("Apple", "Banana", "Cherry"), titles(sorted))
    }

    @Test
    fun `sort is case-insensitive at PRIMARY strength`() {
        val input = listOf(
            song(1, "banana"),
            song(2, "Apple"),
            song(3, "cherry")
        )

        val sorted = sorter.sort(input)

        assertEquals(listOf("Apple", "banana", "cherry"), titles(sorted))
    }

    @Test
    fun `sorts by natural order so Track2 precedes Track10`() {
        val input = listOf(
            song(10, "Track10"),
            song(2, "Track2"),
            song(1, "Track1")
        )

        val sorted = sorter.sort(input)

        assertEquals(listOf("Track1", "Track2", "Track10"), titles(sorted))
    }

    @Test
    fun `natural order continues comparing segments after equal numbers`() {
        val input = listOf(
            song(1, "Track2 Live"),
            song(2, "Track02")
        )

        val sorted = sorter.sort(input)

        assertEquals(listOf("Track02", "Track2 Live"), titles(sorted))
    }

    @Test
    fun `falls back to filename without extension when title is empty`() {
        val input = listOf(
            song(1, "", path = "/storage/emulated/0/Music/zebra.mp3"),
            song(2, "apple")
        )

        val sorted = sorter.sort(input)

        assertEquals(listOf("apple", ""), titles(sorted))
        assertEquals("/storage/emulated/0/Music/zebra.mp3", sorted[1].path)
    }

    @Test
    fun `falls back to filename when title is blank`() {
        val input = listOf(
            song(1, "   ", path = "/storage/emulated/0/Music/song.mp3"),
            song(2, "another")
        )

        val sorted = sorter.sort(input)

        assertEquals("another", sorted[0].title)
        assertEquals("   ", sorted[1].title)
    }

    @Test
    fun `songs with identical title break tie by id ascending`() {
        val input = listOf(
            song(id = 30, title = "Same Title"),
            song(id = 10, title = "Same Title"),
            song(id = 20, title = "Same Title")
        )

        val sorted = sorter.sort(input)

        assertEquals(listOf(10L, 20L, 30L), sorted.map { it.id })
    }

    @Test
    fun `overflow-length numeric segments do not throw`() {
        val huge = "9".repeat(25)
        val input = listOf(
            song(1, "Track$huge"),
            song(2, "Track${huge}1")
        )

        assertDoesNotThrow { sorter.sort(input) }
    }

    @Test
    fun `chinese titles do not throw`() {
        val input = listOf(
            song(1, "晴天"),
            song(2, "七里香"),
            song(3, "稻香")
        )

        assertDoesNotThrow { sorter.sort(input) }
    }

    @Test
    fun `empty list returns empty list`() {
        assertEquals(emptyList<Song>(), sorter.sort(emptyList()))
    }

    @Test
    fun `single-element list returns same list`() {
        val input = listOf(song(1, "Solo"))

        val sorted = sorter.sort(input)

        assertEquals(titles(input), titles(sorted))
    }

    private fun assertDoesNotThrow(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            throw AssertionError("Expected no exception, but got: $e", e)
        }
    }
}
