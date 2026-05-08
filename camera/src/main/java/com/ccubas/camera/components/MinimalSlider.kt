package com.ccubas.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TRACK_HEIGHT = 2.dp
private val THUMB_SIZE = 14.dp
private val INACTIVE_TRACK_COLOR = Color.White.copy(alpha = 0.3f)
private val ACTIVE_TRACK_COLOR = Color.White

@Composable
private fun MinimalThumb() {
    Box(
        Modifier
            .size(THUMB_SIZE)
            .clip(CircleShape)
            .background(Color.White)
    )
}

@Composable
private fun MinimalSingleTrack(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
            .clip(CircleShape)
            .background(INACTIVE_TRACK_COLOR)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(ACTIVE_TRACK_COLOR, CircleShape)
        )
    }
}

@Composable
private fun MinimalRangeTrack(startFrac: Float, endFrac: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
            .clip(CircleShape)
            .background(INACTIVE_TRACK_COLOR)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val totalW = maxWidth
            val s = startFrac.coerceIn(0f, 1f)
            val e = endFrac.coerceIn(0f, 1f)
            val activeFrac = (e - s).coerceAtLeast(0f)
            if (activeFrac > 0f) {
                Box(
                    Modifier
                        .offset(x = totalW * s)
                        .width(totalW * activeFrac)
                        .fillMaxHeight()
                        .background(ACTIVE_TRACK_COLOR, CircleShape)
                )
            }
        }
    }
}

/**
 * Single-value slider with a thin white line and small white circle thumb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        enabled = enabled,
        thumb = { MinimalThumb() },
        track = { sliderState ->
            val span = (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                .takeIf { it > 0f } ?: 1f
            val fraction = (sliderState.value - sliderState.valueRange.start) / span
            MinimalSingleTrack(fraction)
        }
    )
}

/**
 * Range slider with the same minimal style as [MinimalSlider]: thin track,
 * small white circle thumbs at both ends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimalRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
) {
    RangeSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        enabled = enabled,
        startThumb = { MinimalThumb() },
        endThumb = { MinimalThumb() },
        track = { rangeState ->
            val span = (rangeState.valueRange.endInclusive - rangeState.valueRange.start)
                .takeIf { it > 0f } ?: 1f
            val startFrac = (rangeState.activeRangeStart - rangeState.valueRange.start) / span
            val endFrac = (rangeState.activeRangeEnd - rangeState.valueRange.start) / span
            MinimalRangeTrack(startFrac, endFrac)
        }
    )
}
