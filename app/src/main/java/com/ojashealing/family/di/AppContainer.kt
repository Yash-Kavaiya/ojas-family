package com.ojashealing.family.di

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import com.google.firebase.storage.storage
import com.ojashealing.family.R
import com.ojashealing.family.data.auth.AuthRepository
import com.ojashealing.family.data.auth.FirebaseAuthRepository
import com.ojashealing.family.data.location.LocationRepository
import com.ojashealing.family.data.points.FirebasePointsRepository
import com.ojashealing.family.data.points.PointsRepository
import com.ojashealing.family.data.settings.SettingsStore
import com.ojashealing.family.data.user.FirestoreUserRepository
import com.ojashealing.family.data.user.UserRepository

// Hand-written DI: one instance of each repository for the whole app (see design §6)
class AppContainer(context: Context) {
    private val app = context.applicationContext

    // Functions are deployed to Mumbai, next to Firestore
    private val functions = Firebase.functions("asia-south1")

    val auth: AuthRepository = FirebaseAuthRepository(Firebase.auth, app.getString(R.string.default_web_client_id))
    val users: UserRepository = FirestoreUserRepository(Firebase.firestore, Firebase.storage)
    val points: PointsRepository = FirebasePointsRepository(Firebase.firestore, functions)
    val settings = SettingsStore(app)
    val locations = LocationRepository { name -> app.assets.open(name).bufferedReader().use { it.readText() } }
}
