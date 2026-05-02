package dev.vitalcc.stukay

import androidx.lifecycle.ViewModel
import dev.vitalcc.stukay.runtime.StukayAppState

class StukayAppViewModel : ViewModel() {
    val appState: StukayAppState = StukayAppState()
}
