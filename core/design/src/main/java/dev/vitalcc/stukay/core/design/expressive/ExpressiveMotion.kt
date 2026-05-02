package dev.vitalcc.stukay.core.design.expressive

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

@Immutable
object ExpressiveMotion {
    const val QuickMs: Int = 180
    const val StandardMs: Int = 260
    const val EmphasisMs: Int = 380

    fun quickFade(): AnimationSpec<Float> = tween(durationMillis = QuickMs)
}
