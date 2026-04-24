package com.rabbithole.musicbbit.navigation

import kotlinx.serialization.Serializable

@Serializable
data object MusicBrowse

@Serializable
data object PlaylistList

@Serializable
data class PlaylistDetail(val playlistId: Long)

@Serializable
data object Alarm

@Serializable
data class AlarmEdit(val alarmId: Long = 0)

@Serializable
data object Player

@Serializable
data object ScanDirectorySettings
