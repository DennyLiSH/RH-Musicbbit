package com.rabbithole.musicbbit.presentation.playback

import androidx.compose.runtime.staticCompositionLocalOf
import com.rabbithole.musicbbit.service.playback.PlaybackSession

/**
 * Composition seam giving Composables access to the application-scoped [PlaybackSession]
 * without a pass-through ViewModel in between. Provided once in AppNavigation; screens
 * read [LocalPlaybackSession.current] and collect [PlaybackSession.playbackState]
 * directly.
 *
 * Replaces the deleted PlayerViewModel (a pure pass-through whose activity-scoped vs
 * nav-backstack-scoped resolution was already inconsistent across screens).
 */
val LocalPlaybackSession = staticCompositionLocalOf<PlaybackSession> {
    error("PlaybackSession not provided — AppNavigation must wrap content in LocalPlaybackSession")
}
