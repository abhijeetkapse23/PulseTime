package com.pulsetime.presentation.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition

/** Minimum touch target on the wrist. */
val MinTouch = 52.dp

/**
 * Standard screen: TimeText on top, a ScalingLazyColumn (built-in rotary crown scrolling)
 * and a position indicator. ScalingLazyColumn auto-centres content, so the same layout works
 * on round and square displays.
 */
@Composable
fun PulseScreen(content: ScalingLazyListScope.() -> Unit) {
    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            content = content
        )
    }
}

@Composable
fun ActionChip(
    label: String,
    secondary: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Chip(
        modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
        onClick = onClick,
        enabled = enabled,
        colors = if (selected) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = if (secondary != null) {
            { Text(secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else null
    )
}

@Composable
fun SwitchChip(
    label: String,
    checked: Boolean,
    secondary: String? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    ToggleChip(
        modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = if (secondary != null) {
            { Text(secondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        } else null,
        toggleControl = { Switch(checked = checked, enabled = enabled) }
    )
}
