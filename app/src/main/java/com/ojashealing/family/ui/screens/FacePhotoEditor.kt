package com.ojashealing.family.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.ojashealing.family.R
import com.ojashealing.family.data.decodeUpright
import com.ojashealing.family.ui.theme.BackgroundDark
import com.ojashealing.family.ui.theme.Spacing
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** The opening animation shows the face photo in a frame this shape (width / height). */
const val FACE_FRAME_RATIO = 0.8f

/** The part of the source picture inside the frame, in source pixels. */
data class FaceCrop(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * The part of a picture ([w] x [h]) that shows in a frame of [viewW] x [viewH] pixels when the picture
 * covers the frame, enlarged by [zoom] and shifted by [pan] from centred.
 */
fun faceCropFor(w: Int, h: Int, viewW: Float, viewH: Float, zoom: Float, pan: Offset): FaceCrop {
    val scale = max(viewW / w, viewH / h) * zoom
    val width = (viewW / scale).roundToInt().coerceIn(1, w)
    val height = (viewH / scale).roundToInt().coerceIn(1, h)
    val left = (((w * scale - viewW) / 2f - pan.x) / scale).roundToInt().coerceIn(0, w - width)
    val top = (((h * scale - viewH) / 2f - pan.y) / scale).roundToInt().coerceIn(0, h - height)
    return FaceCrop(left, top, width, height)
}

/** Keeps the picture covering the frame: moving it never shows an empty edge. */
fun faceClampPan(w: Int, h: Int, viewW: Float, viewH: Float, zoom: Float, pan: Offset): Offset {
    val scale = max(viewW / w, viewH / h) * zoom
    val maxX = (w * scale - viewW) / 2f
    val maxY = (h * scale - viewH) / 2f
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

/**
 * How see-through a pixel is, from how sure the model is that it shows the person (0 to 1). Sure
 * pixels stay, unsure ones fade over a band, so hair and shoulders get a soft edge, not a cut one.
 */
fun personAlpha(confidence: Float): Int = (((confidence - 0.25f) / 0.5f).coerceIn(0f, 1f) * 255f).roundToInt()

/**
 * The person without what is behind them. Runs on the phone (ML Kit selfie segmentation); the photo
 * is not sent anywhere for this. Throws when the photo cannot be read by the model.
 */
suspend fun removeBackground(src: Bitmap): Bitmap = withContext(Dispatchers.Default) {
    val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).enableRawSizeMask().build()
    )
    try {
        val mask = segmenter.process(InputImage.fromBitmap(src, 0)).await()
        val confidence = FloatArray(mask.width * mask.height)
        mask.buffer.apply { rewind() }.asFloatBuffer().get(confidence)
        val pixels = IntArray(confidence.size) { personAlpha(confidence[it]) shl 24 }
        // Nothing that looks like a person: better to say so than to send an empty picture
        check(pixels.count { it != 0 } > pixels.size / 50) { "No person found" }
        val small = Bitmap.createBitmap(pixels, mask.width, mask.height, Bitmap.Config.ARGB_8888)
        val out = src.copy(Bitmap.Config.ARGB_8888, true).apply { setHasAlpha(true) }
        // Keep the photo only where the mask is solid; stretching the mask with filtering softens its edge
        val keep = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        }
        android.graphics.Canvas(out).drawBitmap(small, null, android.graphics.Rect(0, 0, out.width, out.height), keep)
        small.recycle()
        out
    } finally {
        segmenter.close()
    }
}

/** The longest the saved photo's height may be: enough for a phone screen, small enough to upload quickly. */
private const val MAX_FACE_HEIGHT = 1200

private suspend fun saveFace(context: Context, bitmap: Bitmap, crop: FaceCrop, transparent: Boolean): Uri = withContext(Dispatchers.IO) {
    var out = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width, crop.height)
    if (out.height > MAX_FACE_HEIGHT) {
        out = Bitmap.createScaledBitmap(out, (out.width * MAX_FACE_HEIGHT.toFloat() / out.height).roundToInt(), MAX_FACE_HEIGHT, true)
    }
    // A cut-out needs PNG to stay see-through; a plain photo is far smaller as JPEG
    val file = File(context.cacheDir, "face_${System.currentTimeMillis()}.${if (transparent) "png" else "jpg"}")
    file.outputStream().use { out.compress(if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, it) }
    Uri.fromFile(file)
}

/**
 * Before the face photo for the opening animation is sent: crop it to the frame the animation uses
 * (pinch to zoom, drag to move) and, if wanted, remove the background. The preview is what the
 * animation will show: the same shape on the same dark background.
 */
@Composable
fun FacePhotoEditor(source: Uri, onDismiss: () -> Unit, onDone: (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var original by remember(source) { mutableStateOf<Bitmap?>(null) }
    var cutout by remember(source) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(source) { mutableStateOf(false) }
    var noBackground by remember(source) { mutableStateOf(false) }
    var cutting by remember { mutableStateOf(false) }
    var cutFailed by remember(source) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var zoom by remember(source) { mutableFloatStateOf(1f) }
    var pan by remember(source) { mutableStateOf(Offset.Zero) }
    var view by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(source) {
        runCatching { context.contentResolver.decodeUpright(source, 1600) }.onSuccess { original = it }.onFailure { failed = true }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.crop_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(stringResource(R.string.crop_hint), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
            val shown = if (noBackground) cutout ?: original else original
            // As tall as the space allows, always in the animation's shape, on the animation's background
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.aspectRatio(FACE_FRAME_RATIO)
                        .clip(RoundedCornerShape(28.dp)).background(BackgroundDark).onSizeChanged { view = it },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        failed -> Text(stringResource(R.string.error_photo_upload), color = Color.White, modifier = Modifier.padding(Spacing.l))
                        shown == null -> CircularProgressIndicator()
                        else -> {
                            val image = remember(shown) { shown.asImageBitmap() }
                            Canvas(
                                Modifier.fillMaxSize().pointerInput(shown.width, shown.height) {
                                    detectTransformGestures { _, move, gestureZoom, _ ->
                                        zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                        pan = faceClampPan(shown.width, shown.height, size.width.toFloat(), size.height.toFloat(), zoom, pan + move)
                                    }
                                }
                            ) {
                                val scale = max(size.width / shown.width, size.height / shown.height) * zoom
                                val dw = shown.width * scale
                                val dh = shown.height * scale
                                drawImage(
                                    image,
                                    dstOffset = IntOffset(((size.width - dw) / 2f + pan.x).roundToInt(), ((size.height - dh) / 2f + pan.y).roundToInt()),
                                    dstSize = IntSize(dw.roundToInt(), dh.roundToInt())
                                )
                            }
                            if (cutting) CircularProgressIndicator()
                        }
                    }
                }
            }
            val source0 = original
            if (source0 != null) {
                Slider(
                    value = zoom,
                    onValueChange = { zoom = it; pan = faceClampPan(source0.width, source0.height, view.width.toFloat(), view.height.toFloat(), it, pan) },
                    valueRange = 1f..5f,
                    modifier = Modifier.fillMaxWidth()
                )
                val setNoBackground: (Boolean) -> Unit = { on ->
                    cutFailed = false
                    if (!on || cutout != null) noBackground = on
                    else {
                        // Worked out once per photo, the first time the switch goes on
                        cutting = true
                        scope.launch {
                            runCatching { removeBackground(source0) }
                                .onSuccess { cutout = it; noBackground = true }
                                .onFailure { cutFailed = true }
                            cutting = false
                        }
                    }
                }
                // The whole row is the switch, so the words can be tapped too
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .toggleable(value = noBackground, enabled = !cutting && !saving, role = Role.Switch, onValueChange = setNoBackground),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.face_remove_background), style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        if (cutFailed) Text(stringResource(R.string.face_remove_background_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Switch(checked = noBackground, enabled = !cutting && !saving, onCheckedChange = null)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel), color = Color.White) }
                Button(
                    onClick = {
                        val picture = shown ?: return@Button
                        saving = true
                        scope.launch {
                            // The same sums as the preview, so what is saved is what was framed
                            val crop = faceCropFor(picture.width, picture.height, view.width.toFloat(), view.height.toFloat(), zoom, pan)
                            runCatching { saveFace(context, picture, crop, transparent = noBackground && cutout != null) }
                                .onSuccess(onDone).onFailure { failed = true }
                            saving = false
                        }
                    },
                    enabled = shown != null && !saving && !cutting && view != IntSize.Zero,
                    modifier = Modifier.weight(1f),
                ) { if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.crop_use)) }
            }
        }
    }
}
