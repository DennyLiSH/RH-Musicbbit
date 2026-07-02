package com.rabbithole.musicbbit.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmNotificationContentBuilderTest {

    private val builder = AlarmNotificationContentBuilder(
        defaultAlarmLabel = "Music Alarm",
        unknownArtist = "Unknown artist",
        playingFormat = "Playing: %1\$s - %2\$s",
        stop = "Stop",
        pause = "Pause",
        resume = "Resume",
        extend = "Extend",
        extendMinutesFormat = "Extend %d min",
        toSongEnd = "To song end",
        alarmPausedTitle = "Alarm Paused",
        playbackPausedText = "Playback has been paused",
    )

    @Test
    fun `buildPlaying sets showFullScreenIntent when requested`() {
        val content = builder.buildPlaying(
            alarmLabel = "Test",
            songTitle = "Song",
            songArtist = "Artist",
            showFullScreenIntent = true
        )

        assertTrue(content.showFullScreenIntent)
    }

    @Test
    fun `buildPlaying does not set showFullScreenIntent when requested false`() {
        val content = builder.buildPlaying(
            alarmLabel = "Test",
            songTitle = "Song",
            songArtist = "Artist",
            showFullScreenIntent = false
        )

        assertFalse(content.showFullScreenIntent)
    }
}
