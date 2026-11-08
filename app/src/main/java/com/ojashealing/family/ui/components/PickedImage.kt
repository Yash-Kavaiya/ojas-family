package com.ojashealing.family.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Copies a picked picture into the app's cache. The photo picker's permission ends when Android
 * stops the app (for example while the admin is in another app copying text), so a saved picker
 * Uri can no longer be read on return; a file of our own always can.
 */
suspend fun Context.keepPickedImage(picked: Uri): Uri = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(cacheDir, "picked").apply { mkdirs() }
        // Pictures older than a day are drafts nobody came back to
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600 * 1000L }?.forEach { it.delete() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        contentResolver.openInputStream(picked)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        Uri.fromFile(file)
    }.getOrDefault(picked)
}

/**
 * An image-only photo picker. [onPicked] gets a copy in the app's cache, so keep it in
 * `rememberSaveable` (a Uri is Parcelable) and the picture survives leaving the app.
 */
@Composable
fun rememberImagePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { onPicked(context.keepPickedImage(uri)) }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
