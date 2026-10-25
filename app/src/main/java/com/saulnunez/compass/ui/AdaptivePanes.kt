package com.saulnunez.compass.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Lays out a screen's notation ([main]) and its controls ([header] and [panel]) for the space there
 * is. On a wide, landscape screen (a phone on its side, a tablet) the controls sit in a column beside
 * the notation, [header] above [panel]. Otherwise they are stacked: [header] above the notation and
 * [panel] below it, taking at most [maxStackedPanelFraction] of the height so the notation keeps room.
 *
 * Every slot stays in the same place in the composition whichever way it is laid out, so the alphaTab
 * view in [main] (and its Playback) carries on across rotation. [panel] is told which way it is laid
 * out and gets a bounded height, so it should scroll what doesn't fit.
 */
@Composable
fun AdaptivePanes(
    modifier: Modifier = Modifier,
    maxStackedPanelFraction: Float = 0.6f,
    header: @Composable () -> Unit = {},
    panel: @Composable (sideBySide: Boolean) -> Unit,
    main: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val sideBySide = maxWidth > maxHeight && maxWidth >= SIDE_BY_SIDE_MIN_WIDTH
        val sideWidth = (maxWidth * SIDE_FRACTION).coerceIn(SIDE_MIN_WIDTH, SIDE_MAX_WIDTH)
        Layout(contents = listOf(main, header, { panel(sideBySide) })) { (mains, headers, panels), constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            if (sideBySide) {
                val columnWidth = sideWidth.roundToPx().coerceAtMost(width / 2)
                val headerPlaceables = headers.map { it.measure(Constraints(maxWidth = columnWidth, maxHeight = height)) }
                val headerHeight = headerPlaceables.maxOfOrNull { it.height } ?: 0
                val panelPlaceables = panels.map {
                    it.measure(Constraints(minWidth = columnWidth, maxWidth = columnWidth, maxHeight = (height - headerHeight).coerceAtLeast(0)))
                }
                val mainPlaceables = mains.map { it.measure(Constraints.fixed(width - columnWidth, height)) }
                layout(width, height) {
                    mainPlaceables.forEach { it.place(0, 0) }
                    headerPlaceables.forEach { it.place(width - columnWidth, 0) }
                    panelPlaceables.forEach { it.place(width - columnWidth, headerHeight) }
                }
            } else {
                val headerPlaceables = headers.map { it.measure(Constraints(maxWidth = width, maxHeight = height)) }
                val headerHeight = headerPlaceables.maxOfOrNull { it.height } ?: 0
                val panelMaxHeight = (height * maxStackedPanelFraction).roundToInt().coerceAtMost(height - headerHeight).coerceAtLeast(0)
                val panelPlaceables = panels.map { it.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = panelMaxHeight)) }
                val panelHeight = panelPlaceables.maxOfOrNull { it.height } ?: 0
                val mainHeight = (height - headerHeight - panelHeight).coerceAtLeast(0)
                val mainPlaceables = mains.map { it.measure(Constraints.fixed(width, mainHeight)) }
                layout(width, height) {
                    headerPlaceables.forEach { it.place(0, 0) }
                    mainPlaceables.forEach { it.place(0, headerHeight) }
                    panelPlaceables.forEach { it.place(0, headerHeight + mainHeight) }
                }
            }
        }
    }
}

private val SIDE_BY_SIDE_MIN_WIDTH: Dp = 600.dp
private const val SIDE_FRACTION = 0.42f
private val SIDE_MIN_WIDTH: Dp = 340.dp
private val SIDE_MAX_WIDTH: Dp = 420.dp
