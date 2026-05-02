package dev.vitalcc.stukay.core.design.expressive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.vitalcc.stukay.core.design.theme.StukayStatusError
import dev.vitalcc.stukay.core.design.theme.StukayStatusOk
import dev.vitalcc.stukay.core.design.theme.StukayStatusWarn

enum class ExpressiveStatusTone {
    Positive,
    Warning,
    Critical,
    Neutral,
}

@Composable
fun ExpressiveStatusPill(
    label: String,
    tone: ExpressiveStatusTone,
    modifier: Modifier = Modifier,
) {
    val background = when (tone) {
        ExpressiveStatusTone.Positive -> StukayStatusOk.copy(alpha = 0.18f)
        ExpressiveStatusTone.Warning -> StukayStatusWarn.copy(alpha = 0.18f)
        ExpressiveStatusTone.Critical -> StukayStatusError.copy(alpha = 0.18f)
        ExpressiveStatusTone.Neutral -> MaterialTheme.colorScheme.secondaryContainer
    }

    val foreground = when (tone) {
        ExpressiveStatusTone.Positive -> StukayStatusOk
        ExpressiveStatusTone.Warning -> StukayStatusWarn
        ExpressiveStatusTone.Critical -> StukayStatusError
        ExpressiveStatusTone.Neutral -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Row(
        modifier = modifier
            .background(color = background, shape = CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
        )
    }
}
