package com.ojashealing.family.data.user

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.content.ContentResolver
import android.net.Uri
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.ojashealing.family.data.shrunkJpeg
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.tasks.await

enum class FacePhotoStatus { Submitted, Approved, Rejected }

/**
 * A member's face photo for the opening animation (facePhotos/{uid}). Member Level 3 may upload one;
 * it shows in their own opening animation only after an admin approves it.
 */
data class FacePhoto(
    val uid: String,
    val photoUrl: String,
    val status: FacePhotoStatus,
    val memberName: String = "",
    val memberPhotoUrl: String = ""
)

private fun DocumentSnapshot.toFacePhoto() = FacePhoto(
    uid = id,
    photoUrl = getString("photoUrl").orEmpty(),
    status = when (getString("status")) {
        "approved" -> FacePhotoStatus.Approved
        "rejected" -> FacePhotoStatus.Rejected
        else -> FacePhotoStatus.Submitted
    }
)

@OptIn(ExperimentalCoroutinesApi::class)
class FacePhotoRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val resolver: ContentResolver
) {

    /** The member's own face photo; null when none was ever uploaded. */
    fun observe(uid: String): Flow<FacePhoto?> = callbackFlow {
        val reg = db.document("facePhotos/$uid").addSnapshotListener { snap, _ ->
            if (snap != null) trySend(if (snap.exists()) snap.toFacePhoto() else null)
        }
        awaitClose { reg.remove() }
    }

    /**
     * Uploads the photo under a new name each time, so a picture an admin approved can never be
     * swapped for another one, then asks for review.
     */
    suspend fun submit(uid: String, photo: Uri): Result<Unit> = runCatching {
        // A photo with its background removed arrives from the editor as a PNG and must stay one: JPEG has no see-through
        val cutOut = photo.lastPathSegment.orEmpty().endsWith(".png", ignoreCase = true)
        val ref = storage.reference.child("users/$uid/face_${System.currentTimeMillis()}.${if (cutOut) "png" else "jpg"}")
        if (cutOut) {
            val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(photo)?.use { it.readBytes() } } ?: error("Could not read the photo")
            ref.putBytes(bytes, StorageMetadata.Builder().setContentType("image/png").build()).await()
        } else {
            ref.putBytes(resolver.shrunkJpeg(photo, 1600), StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        }
        val url = ref.downloadUrl.await().toString()
        db.document("facePhotos/$uid").set(
            mapOf("status" to "submitted", "photoUrl" to url, "submittedAt" to FieldValue.serverTimestamp())
        ).await()
        Unit
    }

    /** For admins: face photos waiting for review, with who sent each one. */
    fun observeSubmissions(): Flow<List<FacePhoto>> = callbackFlow {
        val reg = db.collection("facePhotos").whereEqualTo("status", "submitted").limit(100).addSnapshotListener { snap, _ ->
            if (snap != null) trySend(snap.documents.map { it.toFacePhoto() })
        }
        awaitClose { reg.remove() }
    }.mapLatest { list ->
        list.map { f ->
            runCatching {
                val d = db.document("users/${f.uid}").get().await()
                f.copy(memberName = d.getString("displayName").orEmpty(), memberPhotoUrl = d.getString("photoUrl").orEmpty())
            }.getOrDefault(f)
        }
    }

    suspend fun review(uid: String, approve: Boolean): Result<Unit> = runCatching {
        db.document("facePhotos/$uid")
            .update(mapOf("status" to if (approve) "approved" else "rejected", "reviewedAt" to FieldValue.serverTimestamp())).await()
        Unit
    }
}
