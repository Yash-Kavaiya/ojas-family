package com.ojashealing.family.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.util.UUID

/**
 * Reads a picked photo as a JPEG no larger than [maxEdge] pixels on its longest side.
 * Storage rules cap uploads at 5 MB and a phone camera photo can be bigger than that.
 */
suspend fun ContentResolver.shrunkJpeg(photo: Uri, maxEdge: Int): ByteArray = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openInputStream(photo).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    val bitmap = openInputStream(photo).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Could not read the photo")
    ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        out.toByteArray()
    }
}

/** Uploads a picked photo for a post and returns its download URL. */
class PostImages(
    private val storage: FirebaseStorage,
    private val resolver: ContentResolver
) {
    /** Member uploads live under their own folder (storage.rules: owner writes, members read). */
    suspend fun upload(uid: String, photo: Uri): String = put("users/$uid/posts/${UUID.randomUUID()}.jpg", photo)

    /** Thought of the Day pictures; storage.rules lets only admins write here. */
    suspend fun uploadThought(thoughtId: String, photo: Uri): String = put("thoughts/$thoughtId.jpg", photo)

    private suspend fun put(path: String, photo: Uri): String {
        val ref = storage.reference.child(path)
        val meta = StorageMetadata.Builder().setContentType("image/jpeg").build()
        ref.putBytes(resolver.shrunkJpeg(photo, 1600), meta).await()
        return ref.downloadUrl.await().toString()
    }
}
