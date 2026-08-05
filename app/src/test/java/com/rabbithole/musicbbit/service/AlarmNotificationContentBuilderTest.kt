package com.rabbithole.musicbbit.service

import org.junit.Assert.assertEquals
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

    // ------------------------------------------------------------------
    // ActionType → action mapping (was previously in AlarmNotificationHelper)
    // ------------------------------------------------------------------

    @Test
    fun `buildPlaying actions carry correct intent action constants`() {
        val content = builder.buildPlaying(
            alarmLabel = "Test",
            songTitle = "Song",
            songArtist = "Artist",
            showFullScreenIntent = false
        )

        // Order: Stop, Pause, ExtendMinutes(5) [extend dropdown],
        //        ExtendMinutes(5) [snooze icon], ExtendMinutes(10), ExtendMinutes(15), ExtendToEnd
        assertEquals(AlarmActionReceiver.ACTION_STOP, content.actions[0].type.action)
        assertEquals(AlarmActionReceiver.ACTION_PAUSE, content.actions[1].type.action)
        assertEquals(AlarmActionReceiver.ACTION_EXTEND_MINUTES, content.actions[2].type.action)
        assertEquals(AlarmActionReceiver.ACTION_EXTEND_MINUTES, content.actions[3].type.action)
        assertEquals(AlarmActionReceiver.ACTION_EXTEND_MINUTES, content.actions[4].type.action)
        assertEquals(AlarmActionReceiver.ACTION_EXTEND_MINUTES, content.actions[5].type.action)
        assertEquals(AlarmActionReceiver.ACTION_EXTEND_TO_END, content.actions[6].type.action)
    }

    @Test
    fun `ExtendMinutes subtype carries minutes in extras`() {
        val content = builder.buildPlaying(
            alarmLabel = "Test",
            songTitle = "Song",
            songArtist = "Artist",
            showFullScreenIntent = false
        )

        val five = content.actions[2].type
        assertTrue(five is AlarmNotificationContent.ActionType.ExtendMinutes)
        assertEquals(5, (five as AlarmNotificationContent.ActionType.ExtendMinutes).minutes)
        assertEquals(5, five.extras[AlarmActionReceiver.EXTRA_MINUTES])

        val fifteen = content.actions[5].type
        assertTrue(fifteen is AlarmNotificationContent.ActionType.ExtendMinutes)
        assertEquals(15, (fifteen as AlarmNotificationContent.ActionType.ExtendMinutes).minutes)
        assertEquals(15, fifteen.extras[AlarmActionReceiver.EXTRA_MINUTES])
    }

    @Test
    fun `non-Minutes ActionTypes have empty extras`() {
        val content = builder.buildPlaying(
            alarmLabel = "Test",
            songTitle = "Song",
            songArtist = "Artist",
            showFullScreenIntent = false
        )

        assertEquals(emptyMap<String, Int?>(), content.actions[0].type.extras) // Stop
        assertEquals(emptyMap<String, Int?>(), content.actions[1].type.extras) // Pause
        assertEquals(emptyMap<String, Int?>(), content.actions[6].type.extras) // ExtendToEnd
    }

    @Test
    fun `buildPaused actions carry Resume and Stop with correct mappings`() {
        val content = builder.buildPaused()

        assertEquals(AlarmActionReceiver.ACTION_RESUME, content.actions[0].type.action)
        assertEquals(AlarmActionReceiver.ACTION_STOP, content.actions[1].type.action)
        assertEquals(emptyMap<String, Int?>(), content.actions[0].type.extras)
        assertEquals(emptyMap<String, Int?>(), content.actions[1].type.extras)
    }
}
