package com.pulsetime.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.pulsetime.PulseController
import com.pulsetime.data.PulseRepository
import com.pulsetime.data.PulseSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import java.time.Instant

/**
 * Tile: status, active interval and a one-tap Start/Pause.
 *
 * Battery notes:
 *  - freshnessIntervalMillis = 0: the tile is never polled. It is re-rendered only when
 *    PulseController pushes an update (start/stop/each pulse).
 *  - The "Next pulse in Xm" countdown is a ProtoLayout *dynamic expression* evaluated by the
 *    system renderer from platform time, so it ticks down without waking our process.
 */
class PulseTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        scope.future {
            if (requestParams.currentState.lastClickableId == ID_TOGGLE) {
                // The tile is already being re-rendered by this request; don't push another.
                PulseController.toggle(applicationContext, refreshTile = false)
            }
            val s = PulseRepository.get(applicationContext)
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(0L)
                .setTileTimeline(
                    TimelineBuilders.Timeline.fromLayoutElement(layout(s, requestParams.deviceConfiguration))
                )
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = scope.future {
        ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun layout(s: PulseSettings, device: DeviceParameters): LayoutElementBuilders.LayoutElement {
        val now = System.currentTimeMillis()
        val toggle = ModifiersBuilders.Clickable.Builder()
            .setId(ID_TOGGLE)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        val title = Text.Builder(this, "PULSETIME")
            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
            .setColor(argb(ACCENT))
            .build()

        val detail = when {
            !s.active -> "Tap Start to begin"
            s.isSnoozed(now) -> "Snoozed · ${s.intervalMinutes}m"
            else -> "Every ${s.intervalMinutes}m · ${s.preset.label}"
        }

        val content = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(statusText(s, now))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).build())
            .addContent(
                Text.Builder(this, detail)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(MUTED))
                    .setMaxLines(2)
                    .build()
            )
            .build()

        val chip = CompactChip.Builder(this, if (s.active) "Pause" else "Start", toggle, device)
            .setChipColors(ChipColors.primaryChipColors(Colors.DEFAULT))
            .build()

        return PrimaryLayout.Builder(device)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(title)
            .setContent(content)
            .setPrimaryChipContent(chip)
            .build()
    }

    /** "Next pulse in 14m", counting down via a platform-time dynamic expression. */
    private fun statusText(s: PulseSettings, now: Long): LayoutElementBuilders.Text {
        val fontStyle = LayoutElementBuilders.FontStyle.Builder()
            .setSize(sp(18f))
            .setColor(argb(WHITE))
            .build()

        if (!s.active || s.nextPulseAtMs <= now) {
            return LayoutElementBuilders.Text.Builder()
                .setText(if (s.active) "Pulsing…" else "Paused")
                .setFontStyle(fontStyle)
                .build()
        }

        val staticMinutes = ((s.nextPulseAtMs - now + 59_999L) / 60_000L)
        val minutesLeft = DynamicInstant.platformTimeWithSecondsPrecision()
            .durationUntil(DynamicInstant.withSecondsPrecision(Instant.ofEpochMilli(s.nextPulseAtMs)))
            .toIntSeconds()
            .plus(59)
            .div(60)
        val dynamicText = DynamicString.constant("Next in ")
            .concat(minutesLeft.format())
            .concat(DynamicString.constant("m"))

        return LayoutElementBuilders.Text.Builder()
            .setText(
                TypeBuilders.StringProp.Builder("Next in ${staticMinutes}m")
                    .setDynamicValue(dynamicText)
                    .build()
            )
            .setLayoutConstraintsForDynamicText(
                TypeBuilders.StringLayoutConstraint.Builder("Next in 000m").build()
            )
            .setFontStyle(fontStyle)
            .setMaxLines(1)
            .build()
    }

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val ID_TOGGLE = "toggle"
        const val ACCENT = 0xFF7FD3FF.toInt()
        const val MUTED = 0xFFB0B0B0.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
