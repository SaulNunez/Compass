package com.saulnunez.compass.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

/*
 * Icons on buttons follow one rule: an icon that says on its own what the button does (play, undo,
 * delete, share, a cursor arrow…) stands alone, with its label read out by screen readers and shown
 * as a tooltip on long press; an icon that doesn't (import, insert a Measure, a Section action…)
 * keeps its label beside it.
 */

/**
 * A button that is only an icon, because [icon] alone says what it does. [label] is read out by
 * screen readers and shown in a tooltip on long press or hover.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(painterResource(icon), contentDescription = label)
        }
    }
}

/** A toolbar button with its label beside [icon], because the icon alone doesn't say what it does. */
@Composable
fun LabeledIconAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
        modifier = modifier,
    ) {
        IconAndLabel(icon, label)
    }
}

/**
 * [icon] followed by [label], sized and spaced for the content of a Material button; use it with
 * `contentPadding = ButtonDefaults.ButtonWithIconContentPadding`. The icon is decorative: the label
 * already names the button.
 */
@Composable
fun RowScope.IconAndLabel(@DrawableRes icon: Int, label: String) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(label)
}
