package com.ojashealing.family.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ojashealing.family.MainActivity
import com.ojashealing.family.R
import kotlinx.coroutines.tasks.await

/** Notification channels; users can also silence each one in Android settings. */
object Channels {
    const val COMMUNITY = "community"
    const val PRACTICE = "practice"
    const val CHAT = "chat"
    const val CALL = "call"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        fun ch(id: String, @StringRes name: Int, importance: Int) = NotificationChannel(id, context.getString(name), importance)
        nm.createNotificationChannels(
            listOf(
                ch(COMMUNITY, R.string.channel_community, NotificationManager.IMPORTANCE_DEFAULT),
                ch(PRACTICE, R.string.channel_practice, NotificationManager.IMPORTANCE_HIGH),
                ch(CHAT, R.string.channel_chat, NotificationManager.IMPORTANCE_HIGH),
                ch(CALL, R.string.channel_call, NotificationManager.IMPORTANCE_HIGH)
            )
        )
    }
}

/** Extra on the launch intent: a Firestore path to open ("questions/x", "chats/uid", "calls/id", "practice/morning"). */
const val EXTRA_TARGET = "ojas.target"

fun canNotify(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

fun showNotification(context: Context, id: Int, channel: String, title: String, body: String, target: String?, urgent: Boolean = false) {
    if (!canNotify(context)) return
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        target?.let { putExtra(EXTRA_TARGET, it) }
    }
    val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val n = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(pending)
        .setAutoCancel(true)
        .setPriority(if (urgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
        .apply {
            if (urgent) {
                setCategory(NotificationCompat.CATEGORY_CALL)
                setFullScreenIntent(pending, true)
                setTimeoutAfter(45_000)
            }
        }
        .build()
    @Suppress("MissingPermission")
    NotificationManagerCompat.from(context).notify(id, n)
}

/**
 * Turns the server's data-only messages into notifications in the user's language. The server already
 * checked personal preferences; broadcast topics are kept in sync with Settings by [PushTopics].
 */
class OjasMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        PushTopics.saveToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        val me = Firebase.auth.currentUser?.uid
        if (me == null || d["fromUid"] == me) return // my own post coming back on a topic
        val who = d["title"].orEmpty()
        val text = d["body"].orEmpty()
        val (channel, title, body) = when (d["type"]) {
            "announcement" -> Triple(Channels.COMMUNITY, getString(R.string.push_announcement), who.ifEmpty { text })
            "thought" -> Triple(Channels.COMMUNITY, getString(R.string.push_thought), who.ifEmpty { text })
            "question" -> Triple(Channels.COMMUNITY, getString(R.string.push_question, who), text)
            "answer" -> Triple(Channels.COMMUNITY, getString(R.string.push_answer, who), text)
            "mention" -> Triple(Channels.COMMUNITY, getString(R.string.push_mention, who), text)
            "daily" -> Triple(Channels.COMMUNITY, getString(R.string.push_daily, who), text)
            "dailyReply" -> Triple(Channels.COMMUNITY, getString(R.string.push_daily_reply, who), text)
            "chat" -> Triple(Channels.CHAT, getString(R.string.push_chat, who), text)
            "call" -> Triple(Channels.CALL, getString(R.string.push_call, who), getString(R.string.call_tap_to_answer))
            else -> return
        }
        val target = d["target"]
        showNotification(this, (target ?: d["type"]).hashCode(), channel, title, body, target, urgent = d["type"] == "call")
    }
}

/** Broadcast notification categories are FCM topics; Settings switches subscribe and unsubscribe. */
object PushTopics {
    // Settings key -> FCM topic (same names as TOPICS in functions/src/notify.ts)
    val topics = mapOf(
        "announcements" to "announcements",
        "thoughtOfDay" to "thoughtOfDay",
        "communityPosts" to "communityPosts",
        "dailyPosts" to "dailyPosts"
    )

    /** [paid]: Daily Activities posts are only for members who can see them. */
    suspend fun sync(prefs: Map<String, Boolean>, paid: Boolean) {
        val all = prefs["all"] ?: true
        val fcm = FirebaseMessaging.getInstance()
        for ((key, topic) in topics) {
            val on = all && (prefs[key] ?: true) && (key != "dailyPosts" || paid)
            runCatching { if (on) fcm.subscribeToTopic(topic).await() else fcm.unsubscribeFromTopic(topic).await() }
        }
    }

    suspend fun registerToken() {
        runCatching { saveToken(FirebaseMessaging.getInstance().token.await()) }
    }

    fun saveToken(token: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        Firebase.firestore.document("users/$uid").set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
    }

    /** On sign-out the phone stops receiving this account's messages. */
    suspend fun forget(uid: String) {
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            Firebase.firestore.document("users/$uid").update("fcmTokens", FieldValue.arrayRemove(token)).await()
            topics.values.forEach { FirebaseMessaging.getInstance().unsubscribeFromTopic(it).await() }
        }
    }
}
