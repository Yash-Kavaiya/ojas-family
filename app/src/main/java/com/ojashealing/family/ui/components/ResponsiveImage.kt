package com.ojashealing.family.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter

/** Shown while a picture loads, before its real shape is known. */
private const val LOADING_RATIO = 4f / 3f

/** Remembered shapes, so a picture scrolled away and back does not jump while it reloads. */
private val knownRatios = object : LinkedHashMap<String, Float>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?) = size > 300
}

/**
 * Width and height to draw a picture of [ratio] (width / height) in [maxWidth], no taller than
 * [maxHeight]. The whole picture always shows: a tall one gets narrower instead of being cut.
 */
fun fitSize(ratio: Float, maxWidth: Dp, maxHeight: Dp): Pair<Dp, Dp> {
    val r = if (ratio.isFinite() && ratio > 0f) ratio else LOADING_RATIO
    val height = maxWidth / r
    return if (height <= maxHeight) maxWidth to height else (maxHeight * r) to maxHeight
}

/**
 * A member's picture at its own shape, whatever size was uploaded: landscape, portrait or square,
 * never cropped. It fills the width and is at most [maxHeight] tall; tapping opens it full screen.
 */
@Composable
fun ResponsiveImage(
    model: Any?,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 480.dp,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null
) {
    val key = model?.toString().orEmpty()
    var ratio by remember(key) { mutableStateOf(knownRatios[key]) }
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val (w, h) = fitSize(ratio ?: LOADING_RATIO, maxWidth, maxHeight)
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) {
                    val size = state.painter.intrinsicSize
                    if (size.width > 0f && size.height > 0f) {
                        val r = size.width / size.height
                        knownRatios[key] = r
                        ratio = r
                    }
                }
            },
            modifier = Modifier
                .size(w, h)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        )
    }
}
