package com.ojashealing.family.data.user

import android.content.ContentResolver
import android.net.Uri
import com.ojashealing.family.data.shrunkJpeg
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Whether a profile snapshot can be trusted. An empty offline cache reports "no document" for a profile
 * that exists on the server; treating that as "not set up" would send an existing user to profile setup.
 */
internal fun isDefinitive(exists: Boolean, fromCache: Boolean): Boolean = exists || !fromCache

interface UserRepository {
    /** Emits null when the profile document does not exist (yet). */
    fun observe(uid: String): Flow<UserProfile?>
    suspend fun saveProfile(uid: String, edit: ProfileEdit): Result<Unit>
    suspend fun uploadAvatar(uid: String, uri: Uri): Result<String>
    /** Writes user-editable settings such as hideProfile, notificationPrefs or reminder times. */
    suspend fun updateSettings(uid: String, fields: Map<String, Any>): Result<Unit>
}

class FirestoreUserRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val resolver: ContentResolver
) : UserRepository {

    override fun observe(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = db.collection("users").document(uid).addSnapshotListener { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snap != null && isDefinitive(snap.exists(), snap.metadata.isFromCache)) {
                trySend(snap.takeIf { it.exists() }?.toUserProfile())
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun saveProfile(uid: String, edit: ProfileEdit): Result<Unit> = runCatching {
        val fields = mapOf(
            "displayName" to edit.displayName.trim(),
            "dob" to edit.dob,
            "gender" to edit.gender,
            "country" to edit.country,
            "state" to edit.state,
            "city" to edit.city.trim(),
            "language" to edit.language,
            "profileComplete" to true
        )
        db.collection("users").document(uid).set(fields, SetOptions.merge()).await()
        Unit
    }

    override suspend fun uploadAvatar(uid: String, uri: Uri): Result<String> = runCatching {
        val ref = storage.reference.child("users/$uid/avatar.jpg")
        // A profile picture never needs more than this; large camera photos would break the 5 MB rule
        ref.putBytes(resolver.shrunkJpeg(uri, 1024), StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        val url = ref.downloadUrl.await().toString()
        db.collection("users").document(uid).set(mapOf("photoUrl" to url), SetOptions.merge()).await()
        url
    }

    override suspend fun updateSettings(uid: String, fields: Map<String, Any>): Result<Unit> = runCatching {
        db.collection("users").document(uid).set(fields, SetOptions.merge()).await()
        Unit
    }
}
