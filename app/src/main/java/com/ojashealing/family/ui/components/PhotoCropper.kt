package com.ojashealing.family.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ojashealing.family.R
import com.ojashealing.family.data.decodeUpright
import com.ojashealing.family.ui.theme.Spacing
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the visible square sits over the source picture, in source pixels. */
data class CropRect(val left: Int, val top: Int, val size: Int)

/**
 * The source square shown in a [view]-pixel square frame when the picture (w x h) is drawn at
 * cover scale times [zoom], shifted by [pan] from centred.
 */
fun cropRectFor(w: Int, h: Int, view: Float, zoom: Float, pan: Offset): CropRect {
    val scale = max(view / w, view / h) * zoom
    val left = (w * scale - view) / 2f - pan.x
    val top = (h * scale - view) / 2f - pan.y
    val size = (view / scale).roundToInt().coerceIn(1, minOf(w, h))
    return CropRect(
        (left / scale).roundToInt().coerceIn(0, w - size),
        (top / scale).roundToInt().coerceIn(0, h - size),
        size
    )
}

/** Keeps the picture covering the frame: pan never shows an empty edge. */
fun clampPan(w: Int, h: Int, view: Float, zoom: Float, pan: Offset): Offset {
    val scale = max(view / w, view / h) * zoom
    val maxX = (w * scale - view) / 2f
    val maxY = (h * scale - view) / 2f
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

/** Where the whole picture sits, centred, inside a [view]-sized square ("Fit"). */
fun fitRect(w: Int, h: Int, view: Float): FitRect {
    val scale = minOf(view / w, view / h)
    val dw = w * scale
    val dh = h * scale
    return FitRect((view - dw) / 2f, (view - dh) / 2f, (view + dw) / 2f, (view + dh) / 2f)
}

data class FitRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun width() = right - left
    fun height() = bottom - top
    fun toRectF() = android.graphics.RectF(left, top, right, bottom)
}

/** A very soft copy of the picture: shrunk to a few pixels and stretched back, used behind a fitted photo. */
private fun blurredBackdrop(src: Bitmap): Bitmap = Bitmap.createScaledBitmap(src, 16, 16, true)

/** "Fit": the whole photo, centred on a square of its own blurred colours. */
private fun fitSquare(src: Bitmap, edge: Int): Bitmap {
    val out = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)
    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    c.drawBitmap(blurredBackdrop(src), null, android.graphics.RectF(0f, 0f, edge.toFloat(), edge.toFloat()), paint)
    c.drawColor(android.graphics.Color.argb(60, 0, 0, 0))
    c.drawBitmap(src, null, fitRect(src.width, src.height, edge.toFloat()).toRectF(), paint)
    return out
}

private suspend fun saveCrop(context: Context, bitmap: Bitmap, rect: CropRect?): Uri = withContext(Dispatchers.IO) {
    val out = if (rect == null) fitSquare(bitmap, minOf(1024, maxOf(bitmap.width, bitmap.height)))
    else Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.size, rect.size)
        .let { if (it.width > 1024) Bitmap.createScaledBitmap(it, 1024, 1024, true) else it }
    val file = File(context.cacheDir, "profile_crop_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    Uri.fromFile(file)
}

/**
 * Full-screen cropper for a profile photo: pinch to zoom, drag to move, round preview.
 * [onCropped] gets a square JPEG (at most 1024 px) in the app cache.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PhotoCropDialog(source: Uri, onDismiss: () -> Unit, onCropped: (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(source) { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var saving by remember { mutableStateOf(false) }
    var viewPx by remember { mutableFloatStateOf(1f) }
    var fit by remember { mutableStateOf(false) } // false: Crop (zoom and move); true: Fit (whole photo)

    LaunchedEffect(source) {
        runCatching { context.contentResolver.decodeUpright(source, 2048) }
            .onSuccess { bitmap = it }.onFailure { failed = true }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.crop_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
            androidx.compose.material3.SingleChoiceSegmentedButtonRow {
                listOf(false to R.string.crop_mode_crop, true to R.string.crop_mode_fit).forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = fit == mode,
                        onClick = { fit = mode },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, 2),
                        label = { Text(stringResource(label)) }
                    )
                }
            }
            Text(stringResource(if (fit) R.string.crop_fit_hint else R.string.crop_hint), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
            val b = bitmap
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).weight(1f, fill = false), contentAlignment = Alignment.Center) {
                val view = with(LocalDensity.current) { minOf(maxWidth, maxHeight).toPx() }
                androidx.compose.runtime.SideEffect { viewPx = view }
                when {
                    failed -> Text(stringResource(R.string.error_photo_upload), color = Color.White)
                    b == null -> CircularProgressIndicator()
                    else -> {
                        val image: ImageBitmap = remember(b) { b.asImageBitmap() }
                        val backdrop: ImageBitmap = remember(b) { blurredBackdrop(b).asImageBitmap() }
                        Canvas(
                            Modifier.fillMaxSize().pointerInput(b, view, fit) {
                                if (fit) return@pointerInput
                                detectTransformGestures { _, move, gestureZoom, _ ->
                                    zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                    pan = clampPan(b.width, b.height, view, zoom, pan + move)
                                }
                            }
                        ) {
                            if (fit) {
                                drawImage(backdrop, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                                drawRect(Color.Black.copy(alpha = 0.24f))
                                val r = fitRect(b.width, b.height, size.width)
                                drawImage(image, dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()), dstSize = IntSize(r.width().roundToInt(), r.height().roundToInt()))
                            } else {
                                val scale = max(size.width / b.width, size.height / b.height) * zoom
                                val dw = b.width * scale
                                val dh = b.height * scale
                                val left = (size.width - dw) / 2f + pan.x
                                val top = (size.height - dh) / 2f + pan.y
                                drawImage(
                                    image,
                                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                    dstSize = IntSize(dw.roundToInt(), dh.roundToInt())
                                )
                            }
                            // Dim everything outside the circle the profile will show
                            val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset.Zero, Size(size.width, size.height))) }
                            clipPath(circle, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.6f)) }
                            drawCircle(Color.White.copy(alpha = 0.9f), radius = size.minDimension / 2f - 1f, style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                }
            }
            if (b != null && !fit) {
                Slider(
                    value = zoom,
                    onValueChange = { zoom = it; pan = clampPan(b.width, b.height, viewPx, it, pan) },
                    valueRange = 1f..5f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel), color = Color.White) }
                Button(
                    onClick = {
                        val src = b ?: return@Button
                        saving = true
                        scope.launch {
                            // Same maths as the preview: pan is in frame pixels
                            val rect = if (fit) null else cropRectFor(src.width, src.height, viewPx, zoom, pan)
                            runCatching { saveCrop(context, src, rect) }
                                .onSuccess(onCropped).onFailure { failed = true }
                            saving = false
                        }
                    },
                    enabled = b != null && !saving,
                    modifier = Modifier.weight(1f)
                ) { if (saving) CircularProgressIndicator(Modifier.padding(2.dp)) else Text(stringResource(R.string.crop_use)) }
            }
        }
    }
}
