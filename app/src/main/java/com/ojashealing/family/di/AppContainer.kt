package com.ojashealing.family.di

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import com.google.firebase.storage.storage
import com.ojashealing.family.R
import com.ojashealing.family.data.auth.AuthRepository
import com.ojashealing.family.data.content.ContentRepository
import com.ojashealing.family.data.content.FirestoreContentRepository
import com.ojashealing.family.data.goals.GoalRepository
import com.ojashealing.family.data.auth.FirebaseAuthRepository
import com.ojashealing.family.data.location.LocationRepository
import com.ojashealing.family.data.points.FirebasePointsRepository
import com.ojashealing.family.data.points.PointsRepository
import com.ojashealing.family.data.settings.SettingsStore
import com.ojashealing.family.data.social.CommunityRepository
import com.ojashealing.family.data.social.ModerationRepository
import com.ojashealing.family.data.social.ThoughtRepository
import com.ojashealing.family.data.user.FirestoreUserRepository
import com.ojashealing.family.data.user.UserRepository
import com.ojashealing.family.data.PostImages
import com.ojashealing.family.data.call.CallRepository
import com.ojashealing.family.data.chat.ChatRepository
import com.ojashealing.family.data.paid.PaidRepository
import com.ojashealing.family.data.paid.PlayBilling
import com.ojashealing.family.data.practice.PracticeRepository
import com.ojashealing.family.data.social.DailyRepository
import com.ojashealing.family.data.user.PublicProfileRepository

// Hand-written DI: one instance of each repository for the whole app (see design §6)
class AppContainer(context: Context) {
    private val app = context.applicationContext

    // Functions are deployed to Mumbai, next to Firestore
    private val functions = Firebase.functions("asia-south1")

    val auth: AuthRepository = FirebaseAuthRepository(Firebase.auth, app.getString(R.string.default_web_client_id))
    val users: UserRepository = FirestoreUserRepository(Firebase.firestore, Firebase.storage, app.contentResolver)
    val points: PointsRepository = FirebasePointsRepository(Firebase.firestore, functions)
    // Ask Swamiji AI: the server answers from Swamiji's teachings; chats stay on the phone
    val ai: com.ojashealing.family.data.ai.AiRepository by lazy {
        com.ojashealing.family.data.ai.RemoteAiRepository(app, Firebase.auth, functions)
    }
    val settings = SettingsStore(app)
    val content: ContentRepository = FirestoreContentRepository(Firebase.firestore, functions)
    val thoughts = ThoughtRepository(Firebase.firestore)
    val community = CommunityRepository(Firebase.firestore)
    val moderation = ModerationRepository(Firebase.firestore)
    val goals = GoalRepository(Firebase.firestore, Firebase.storage, app.contentResolver)
    val practice = PracticeRepository(Firebase.firestore, functions)
    val presence = com.ojashealing.family.data.presence.PresenceRepository(Firebase.firestore)
    val commentLikes = com.ojashealing.family.data.social.CommentLikeRepository(Firebase.firestore)
    val practiceMedia = com.ojashealing.family.data.practice.PracticeMediaRepository(Firebase.firestore, functions)
    val paid = PaidRepository(Firebase.firestore, functions)
    val billing by lazy { PlayBilling(app, paid) }
    val daily = DailyRepository(Firebase.firestore)
    val chat = ChatRepository(Firebase.firestore)
    val calls = CallRepository(Firebase.firestore, functions)
    // The one live call for the whole app, so leaving the call screen does not end it
    val callManager by lazy { com.ojashealing.family.data.call.CallManager(app, calls) }
    val publicProfiles = PublicProfileRepository(Firebase.firestore)
    val images = PostImages(Firebase.storage, app.contentResolver)
    val about = com.ojashealing.family.data.about.AboutRepository(Firebase.firestore)
    val callRequests = com.ojashealing.family.data.call.CallRequestRepository(Firebase.firestore)
    val locations = LocationRepository { name -> app.assets.open(name).bufferedReader().use { it.readText() } }
}
