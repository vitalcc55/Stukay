package dev.vitalcc.stukay.feature.diagnostics.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vitalcc.stukay.core.design.expressive.ExpressiveCard

private val diagnosticsPlaceholders = listOf(
    "Recent logs will appear here after logging core lands.",
    "Current screen summary will move here from app state.",
    "Latest warning/error snapshot will become part of the diagnostics backbone.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsRoute(
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Diagnostics")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            modifier = Modifier.padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ExpressiveCard(
                    title = "Runtime summary",
                    subtitle = "Foundation placeholder before logging core and diagnostics provider.",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
                        Text(text = "Diagnostics is intentionally routed through Settings.")
                    }
                }
            }

            items(diagnosticsPlaceholders) { line ->
                ExpressiveCard(title = line) {
                    Text(text = "This slot will be wired in the next stage of the milestone.")
                }
            }
        }
    }
}
