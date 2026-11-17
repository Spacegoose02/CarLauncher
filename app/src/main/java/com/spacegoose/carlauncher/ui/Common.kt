package com.spacegoose.carlauncher.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

val CardShape = RoundedCornerShape(24.dp)

/** Minimum touch target for anything used while driving. */
val TouchTarget = 64.dp

@Composable
fun DashCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Box(Modifier.padding(20.dp), content = content)
    }
}

/**
 * A font size that fits [chars] characters in [maxWidth] and takes at most [heightFraction] of [maxHeight].
 * Cards are sized by the screen (and shrink in split-screen), so their big text scales with them.
 */
@androidx.compose.runtime.Composable
fun fitText(
    maxWidth: androidx.compose.ui.unit.Dp,
    maxHeight: androidx.compose.ui.unit.Dp,
    chars: Int,
    heightFraction: Float,
    max: androidx.compose.ui.unit.Dp = 400.dp,
): androidx.compose.ui.unit.TextUnit {
    val byHeight = maxHeight * heightFraction
    val byWidth = maxWidth / (chars.coerceAtLeast(1) * 0.62f)
    val size = minOf(byHeight, byWidth, max).coerceAtLeast(10.dp)
    return with(androidx.compose.ui.platform.LocalDensity.current) { size.toSp() }
}
