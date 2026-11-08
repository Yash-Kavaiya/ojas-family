package com.ojashealing.family.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.ojashealing.family.MainActivity
import com.ojashealing.family.R
import com.ojashealing.family.data.call.CallManager
import com.ojashealing.family.data.call.RING_TIMEOUT_MS

/**
 * The ringing call when the app is not on screen: full screen over the lock screen, the phone's
 * ringtone looping, vibration, and Answer / Decline buttons, like a phone call.
 */
object IncomingCallNotifier {
    /** Its own channel: the ringtone sound is fixed when a channel is created. */
    const val CHANNEL = "incoming_call"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ch = NotificationChannel(CHANNEL, context.getString(R.string.channel_incoming_call), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            enableVibration(true)
            vibrationPattern = CallManager.RING_VIBRATION
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun idFor(callId: String) = ("call:$callId").hashCode()

    private fun open(context: Context, callId: String, answer: Boolean, code: Int) = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET, if (answer) "calls/$callId/answer" else "calls/$callId")
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun show(context: Context, callId: String, callerName: String, video: Boolean) {
        if (!canNotify(context)) return
        val id = idFor(callId)
        val full = open(context, callId, answer = false, code = id)
        val answer = open(context, callId, answer = true, code = id + 1)
        val decline = PendingIntent.getBroadcast(
            context, id + 2,
            Intent(context, CallActionReceiver::class.java).setAction(CallActionReceiver.DECLINE).putExtra(CallActionReceiver.CALL_ID, callId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val name = callerName.ifBlank { context.getString(R.string.role_swamiji) }
        val caller = Person.Builder().setName(name).setImportant(true).build()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText(context.getString(if (video) R.string.push_video_call else R.string.push_call, name))
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decline, answer).setIsVideo(video))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(RING_TIMEOUT_MS + 5_000)
            .build()
            // The ringtone repeats until answered, declined or timed out
            .apply { flags = flags or Notification.FLAG_INSISTENT }
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, n)
    }

    fun cancel(context: Context, callId: String) {
        NotificationManagerCompat.from(context).cancel(idFor(callId))
    }

    /** Android 14+: full-screen calls need the user's OK; false means the ring shows only as a banner. */
    fun canRingFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}

/** "Decline" on the ringing notification: tells the caller without opening the app. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(CALL_ID) ?: return
        if (intent.action != DECLINE) return
        IncomingCallNotifier.cancel(context, callId)
        val pending = goAsync()
        Firebase.firestore.document("calls/$callId")
            .update(mapOf("status" to "declined", "endedAt" to FieldValue.serverTimestamp()))
            .addOnCompleteListener { pending.finish() }
    }

    companion object {
        const val DECLINE = "ojas.call.DECLINE"
        const val CALL_ID = "callId"
    }
}
