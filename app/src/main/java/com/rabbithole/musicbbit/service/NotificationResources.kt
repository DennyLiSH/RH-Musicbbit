package com.rabbithole.musicbbit.service

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [Context.getString] with a fallback for [android.content.res.Resources.NotFoundException].
 *
 * Production never throws (resources are baked in); the fallback only triggers under
 * Robolectric when a string resource is not registered with the test config.
 * Always pass an English fallback — it must not depend on the user's locale.
 */
@Singleton
class NotificationResources @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getString(@StringRes resId: Int, fallback: String): String = try {
        context.getString(resId)
    } catch (_: android.content.res.Resources.NotFoundException) {
        fallback
    }
}
