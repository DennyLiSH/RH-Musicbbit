package com.rabbithole.musicbbit.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbithole.musicbbit.domain.repository.AlarmRepository
import com.rabbithole.musicbbit.service.MusicPlayerStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    val stateHolder: MusicPlayerStateHolder,
    private val alarmRepository: AlarmRepository,
) : ViewModel() {

    val playbackState = stateHolder.playbackState

    val alarmLabel: StateFlow<String?> = playbackState
        .map { it.alarmId }
        .distinctUntilChanged()
        .flatMapLatest { alarmId ->
            flow {
                val label = if (alarmId != null) {
                    try {
                        alarmRepository.getAlarmById(alarmId)?.label
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to load alarm label for id=$alarmId")
                        null
                    }
                } else {
                    null
                }
                emit(label)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
