package com.rabbithole.musicbbit.presentation.playback

import androidx.compose.runtime.staticCompositionLocalOf
import com.rabbithole.musicbbit.service.playback.UserPlaybackSession

/**
 * Composition seam giving Composables access to the application-scoped [UserPlaybackSession]
 * without a pass-through ViewModel in between. Provided once in AppNavigation; screens
 * read [LocalPlaybackSession.current] and collect [UserPlaybackSession.playbackState]
 * directly.
 *
 * Replaces the deleted PlayerViewModel (a pure pass-through whose activity-scoped vs
 * nav-backstack-scoped resolution was already inconsistent across screens).
 */
val LocalPlaybackSession = staticCompositionLocalOf<UserPlaybackSession> {
    error("UserPlaybackSession not provided — AppNavigation must wrap content in LocalPlaybackSession")
}
