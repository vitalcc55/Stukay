package dev.vitalcc.stukay

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.vitalcc.stukay.runtime.StukayAppState

class StukayAppViewModel(
    application: Application,
) : AndroidViewModel(application) {
    val appState: StukayAppState = StukayAppState(application)
}
