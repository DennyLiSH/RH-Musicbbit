package com.rabbithole.musicbbit.presentation.player

import androidx.lifecycle.ViewModel
import com.rabbithole.musicbbit.service.MusicPlayerStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val stateHolder: MusicPlayerStateHolder
) : ViewModel()
