package com.rabbithole.musicbbit.domain.model

sealed interface AutoStop {
    data class ByMinutes(val minutes: Int) : AutoStop
    data class BySongCount(val count: Int) : AutoStop
}
