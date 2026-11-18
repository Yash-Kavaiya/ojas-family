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

/** Turns of the EXIF orientation tag; phones often store photos sideways and set only this tag. */
private fun ContentResolver.exifRotation(photo: Uri): Int = runCatching {
    openInputStream(photo).use { input ->
        when (android.media.ExifInterface(input!!).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }
}.getOrDefault(0)

/** Decodes a picked photo upright, no larger than about [maxEdge] pixels on its longest side. */
suspend fun ContentResolver.decodeUpright(photo: Uri, maxEdge: Int): Bitmap = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openInputStream(photo).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    val bitmap = openInputStream(photo).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Could not read the photo")
    val turn = exifRotation(photo)
    if (turn == 0) bitmap
    else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, android.graphics.Matrix().apply { postRotate(turn.toFloat()) }, true)
        .also { if (it !== bitmap) bitmap.recycle() }
}

/**
 * Reads a picked photo as a JPEG no larger than [maxEdge] pixels on its longest side.
 * Storage rules cap uploads at 5 MB and a phone camera photo can be bigger than that.
 */
suspend fun ContentResolver.shrunkJpeg(photo: Uri, maxEdge: Int): ByteArray = withContext(Dispatchers.IO) {
    val bitmap = decodeUpright(photo, maxEdge)
    ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        out.toByteArray()
    }
}

/**
 * Upload metadata with a cache lifetime. Files saved under a new name each time never change, so
 * phones and browsers may keep them for a year; [reusedName] is for paths written again in place
 * (a thought's picture, a resubmitted goal photo). "private": chat files must not sit in shared caches.
 */
fun uploadMetadata(contentType: String, reusedName: Boolean = false): StorageMetadata =
    StorageMetadata.Builder()
        .setContentType(contentType)
        .setCacheControl(if (reusedName) "private, max-age=3600" else "private, max-age=31536000")
        .build()

/** Like [shrunkJpeg] but keeps a PNG's see-through background (a cut-out face photo). */
suspend fun ContentResolver.shrunkPng(photo: Uri, maxEdge: Int): ByteArray = withContext(Dispatchers.IO) {
    val bitmap = decodeUpright(photo, maxEdge)
    ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
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

    /**
     * A member's photo, audio or video for a Q&A question or an Activity post (storage.rules: the
     * member's own folder; audio up to 30 MB, video up to 100 MB). Photos are shrunk like any post photo.
     */
    suspend fun uploadMemberMedia(uid: String, media: Uri, kind: com.ojashealing.family.data.social.MediaKind): String {
        if (kind == com.ojashealing.family.data.social.MediaKind.Image) return upload(uid, media)
        val type = resolver.getType(media) ?: if (kind == com.ojashealing.family.data.social.MediaKind.Audio) "audio/mpeg" else "video/mp4"
        val ext = type.substringAfter('/').substringBefore(';').ifEmpty { "bin" }.take(5)
        val ref = storage.reference.child("users/$uid/media/${UUID.randomUUID()}.$ext")
        ref.putFile(media, uploadMetadata(type)).await()
        return ref.downloadUrl.await().toString()
    }

    /** Thought of the Day pictures; storage.rules lets only admins write here. */
    suspend fun uploadThought(thoughtId: String, photo: Uri): String = put("thoughts/$thoughtId.jpg", photo)

    /**
     * Announcement attachment (admins only, storage.rules). Pictures are shrunk to JPEG; audio and
     * video go up as picked, streamed from the file, with their own content type.
     */
    suspend fun uploadAnnouncement(media: Uri, kind: com.ojashealing.family.data.social.MediaKind): String {
        val id = UUID.randomUUID().toString()
        if (kind == com.ojashealing.family.data.social.MediaKind.Image) return put("announcements/$id.jpg", media)
        val type = resolver.getType(media) ?: if (kind == com.ojashealing.family.data.social.MediaKind.Audio) "audio/mpeg" else "video/mp4"
        val ext = type.substringAfter('/').substringBefore(';').ifEmpty { "bin" }
        val ref = storage.reference.child("announcements/$id.$ext")
        ref.putFile(media, uploadMetadata(type)).await()
        return ref.downloadUrl.await().toString()
    }

    /**
     * A photo, PDF or audio file for a chat (storage.rules: the member and staff, 20 MB).
     * Photos are shrunk; other files go up as they are. Returns (url, name, mime, size).
     */
    suspend fun uploadChatFile(collection: String, memberUid: String, file: Uri): com.ojashealing.family.data.chat.Attachment {
        // A voice note recorded by the app is a plain file, which the content resolver knows nothing about
        val local = if (file.scheme == "file") file.path?.let { java.io.File(it) } else null
        val type = if (local != null) (if (local.extension == "m4a") "audio/mp4"
            else android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(local.extension) ?: "application/octet-stream")
            else resolver.getType(file) ?: "application/octet-stream"
        val name = if (local != null) local.name to local.length()
            else resolver.query(file, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) to c.getLong(1) else null }
        val id = UUID.randomUUID().toString()
        if (type.startsWith("image/")) {
            val bytes = resolver.shrunkJpeg(file, 1600)
            val ref = storage.reference.child("$collection/$memberUid/$id.jpg")
            ref.putBytes(bytes, uploadMetadata("image/jpeg")).await()
            return com.ojashealing.family.data.chat.Attachment(ref.downloadUrl.await().toString(), name?.first ?: "photo.jpg", "image/jpeg", bytes.size.toLong())
        }
        val ext = name?.first?.substringAfterLast('.', "")?.take(5)?.ifEmpty { null } ?: type.substringAfter('/').take(5)
        val ref = storage.reference.child("$collection/$memberUid/$id.$ext")
        ref.putFile(file, uploadMetadata(type)).await()
        return com.ojashealing.family.data.chat.Attachment(ref.downloadUrl.await().toString(), name?.first ?: "file.$ext", type, name?.second ?: 0L)
    }

    private suspend fun put(path: String, photo: Uri): String {
        val ref = storage.reference.child(path)
        // Thought pictures keep the thought's id as their name; every other photo gets a new one
        val meta = uploadMetadata("image/jpeg", reusedName = path.startsWith("thoughts/"))
        ref.putBytes(resolver.shrunkJpeg(photo, 1600), meta).await()
        return ref.downloadUrl.await().toString()
    }
}
