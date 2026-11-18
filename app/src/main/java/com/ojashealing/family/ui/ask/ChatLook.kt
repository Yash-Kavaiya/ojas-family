package com.ojashealing.family.ui.ask

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** The chat's colours, after the familiar messenger look: green for mine, white for theirs, a soft wallpaper. */
@Immutable
data class ChatColors(
    val wallpaper: Color,
    val doodle: Color,
    val header: Color,
    val onHeader: Color,
    val mine: Color,
    val theirs: Color,
    val onBubble: Color,
    val meta: Color,
    val seen: Color,
    val inputPill: Color,
    val send: Color,
    val chip: Color,
    val onChip: Color,
)

@Composable
fun chatColors(): ChatColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) ChatColors(
        wallpaper = Color(0xFF0B141A), doodle = Color(0x0FFFFFFF),
        header = Color(0xFF202C33), onHeader = Color(0xFFE9EDEF),
        mine = Color(0xFF005C4B), theirs = Color(0xFF202C33), onBubble = Color(0xFFE9EDEF),
        meta = Color(0x99E9EDEF), seen = Color(0xFF53BDEB),
        inputPill = Color(0xFF2A3942), send = Color(0xFF00A884),
        chip = Color(0xFF182229), onChip = Color(0xFF8696A0),
    ) else ChatColors(
        wallpaper = Color(0xFFEFE7DD), doodle = Color(0x14000000),
        header = Color(0xFF008069), onHeader = Color.White,
        mine = Color(0xFFD9FDD3), theirs = Color.White, onBubble = Color(0xFF111B21),
        meta = Color(0xFF667781), seen = Color(0xFF53BDEB),
        inputPill = Color.White, send = Color(0xFF00A884),
        chip = Color(0xFFFFFFFF), onChip = Color(0xFF54656F),
    )
}

/** A light pattern of small rings, dots and petals behind the messages, drawn (no picture file). */
fun Modifier.chatWallpaper(c: ChatColors): Modifier = drawBehind {
    drawRect(c.wallpaper)
    val step = 56.dp.toPx()
    val r = 5.dp.toPx()
    val stroke = Stroke(1.2.dp.toPx())
    var row = 0
    var y = step / 2
    while (y < size.height + step) {
        var x = if (row % 2 == 0) step / 2 else step
        var i = row
        while (x < size.width + step) {
            when (i % 3) {
                0 -> drawCircle(c.doodle, r, Offset(x, y), style = stroke)
                1 -> drawCircle(c.doodle, r / 2.5f, Offset(x, y))
                else -> {
                    drawCircle(c.doodle, r / 1.6f, Offset(x - r / 1.4f, y), style = stroke)
                    drawCircle(c.doodle, r / 1.6f, Offset(x + r / 1.4f, y), style = stroke)
                }
            }
            x += step
            i++
        }
        y += step * 0.8f
        row++
    }
}

/**
 * A message bubble: rounded, with a small pointed tail at the top corner on the sender's side
 * when it is the first of a run of messages from the same person.
 */
class BubbleShape(private val mine: Boolean, private val tail: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = with(density) { 10.dp.toPx() }
        val t = with(density) { 8.dp.toPx() }
        val path = Path()
        // The body is inset by the tail's width on the tail side, so every bubble lines up
        val body = Rect(if (mine) 0f else t, 0f, if (mine) size.width - t else size.width, size.height)
        val tl = if (tail && !mine) 0f else radius
        val tr = if (tail && mine) 0f else radius
        path.addRoundRect(
            RoundRect(body, CornerRadius(tl), CornerRadius(tr), CornerRadius(radius), CornerRadius(radius))
        )
        if (tail) {
            if (mine) {
                path.moveTo(body.right - 1f, 0f)
                path.lineTo(size.width, 0f)
                path.lineTo(body.right - 1f, t * 1.3f)
            } else {
                path.moveTo(body.left + 1f, 0f)
                path.lineTo(0f, 0f)
                path.lineTo(body.left + 1f, t * 1.3f)
            }
            path.close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?) = other is BubbleShape && other.mine == mine && other.tail == tail
    override fun hashCode() = (if (mine) 1 else 0) * 2 + if (tail) 1 else 0
}

/** The bubble's padding on the tail side, matching [BubbleShape]'s inset. */
val BubbleTailWidth = 8.dp
