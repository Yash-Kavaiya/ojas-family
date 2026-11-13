package com.ojashealing.family.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * One line of text that shrinks a little to fit instead of being cut off ("Announcemen",
 * "Communi…"). Labels in Hindi and Gujarati, or with large system text, stay whole.
 */
@Composable
fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier, minScale: Float = 0.7f) {
    val full: TextUnit = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    BasicText(
        text = text,
        style = style.merge(color = LocalContentColor.current, textAlign = TextAlign.Center),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = full * minScale, maxFontSize = full, stepSize = 0.5.sp),
        modifier = modifier,
    )
}
