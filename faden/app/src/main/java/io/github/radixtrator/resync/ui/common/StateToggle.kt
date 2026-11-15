package io.github.radixtrator.resync.ui.common

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A vote or save button: a plain icon tinted [checkedColor] while [checked] and [color] otherwise. An
 * [IconToggleButton], so the on state also reaches accessibility services, not only the tint.
 */
@Composable
fun StateToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    description: String,
    checkedColor: Color,
    color: Color,
    modifier: Modifier = Modifier,
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = IconButtonDefaults.iconToggleButtonColors(contentColor = color, checkedContentColor = checkedColor),
    ) {
        Icon(icon, description)
    }
}
