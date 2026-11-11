package com.ojashealing.family.push

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ojashealing.family.R
import com.ojashealing.family.data.call.IST
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A confirmed audio or video call this phone should remind about. */
internal data class CallReminderPlan(
    val id: String,
    val at: Long,
    val video: Boolean,
    val peer: String,
)

internal const val CALL_REMIND_BEFORE_MS = 15 * 60 * 1000L

/** After this, the start reminder is no longer worth keeping. */
internal const val CALL_REMIND_LATE_MS = 10 * 60 * 1000L

/** Confirmed calls whose start is still ahead, or only a few minutes past. */
internal fun callRemindersToKeep(plans: List<CallReminderPlan>, now: Long): List<CallReminderPlan> =
    plans.filter { it.id.isNotEmpty() && it.at > 0 && now <= it.at + CALL_REMIND_LATE_MS }
        .distinctBy { it.id }
        .sortedBy { it.at }

/**
 * Alarm times still in the future. "soon" is 15 minutes before the call. "now" is the start.
 * A time that has already passed is left out, so opening the app again does not repeat it.
 */
internal fun callAlarmInstants(at: Long, now: Long): List<Pair<String, Long>> = buildList {
    val soon = at - CALL_REMIND_BEFORE_MS
    if (soon > now) add("soon" to soon)
    if (at > now) add("now" to at)
}

/**
 * Local reminders for confirmed calls. They stay on the phone until the call, including after a
 * reboot, and they ring for audio and video whether or not the internet is on at that moment.
 */
object CallReminders {
    private const val PREFS = "call_reminders"
    private const val SEP = "\u001f"

    internal fun sync(context: Context, plans: List<CallReminderPlan>, now: Long = System.currentTimeMillis()) {
        val keep = callRemindersToKeep(plans, now)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = decode(prefs.getStringSet("items", emptySet()).orEmpty())
        val am = context.getSystemService(AlarmManager::class.java)
        for (plan in previous) {
            for (phase in listOf("soon", "now")) am.cancel(pending(context, plan, phase))
        }
        prefs.edit().putStringSet("items", encode(keep)).apply()
        for (plan in keep) {
            for ((phase, whenMs) in callAlarmInstants(plan.at, now)) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pending(context, plan, phase))
            }
        }
    }

    /** After a reboot, put back the reminders that were saved. */
    internal fun reschedule(context: Context) {
        val saved = decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("items", emptySet()).orEmpty())
        sync(context, saved)
    }

    internal fun clear(context: Context) = sync(context, emptyList())

    private fun encode(plans: List<CallReminderPlan>): Set<String> = plans.map { plan ->
        listOf(plan.id, plan.at.toString(), if (plan.video) "1" else "0", plan.peer.replace(SEP, " ").take(80)).joinToString(SEP)
    }.toSet()

    private fun decode(raw: Set<String>): List<CallReminderPlan> = raw.mapNotNull { line ->
        val parts = line.split(SEP)
        if (parts.size < 4) return@mapNotNull null
        CallReminderPlan(parts[0], parts[1].toLongOrNull() ?: return@mapNotNull null, parts[2] == "1", parts[3])
    }

    private fun pending(context: Context, plan: CallReminderPlan, phase: String): PendingIntent {
        val intent = Intent(context, CallReminderReceiver::class.java)
            .putExtra("id", plan.id)
            .putExtra("at", plan.at)
            .putExtra("video", plan.video)
            .putExtra("peer", plan.peer)
            .putExtra("phase", phase)
        return PendingIntent.getBroadcast(
            context,
            (plan.id + ":" + phase).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class CallReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id")?.takeIf { it.isNotEmpty() } ?: return
        val at = intent.getLongExtra("at", 0L)
        if (at <= 0L) return
        val video = intent.getBooleanExtra("video", false)
        val peer = intent.getStringExtra("peer").orEmpty().ifEmpty { context.getString(R.string.role_swamiji) }
        val phase = intent.getStringExtra("phase")
        val now = System.currentTimeMillis()
        val show = when (phase) {
            // Inexact alarms can arrive a few minutes off the set time. Drop one that is far outside its window.
            "soon" -> now in (at - 25 * 60_000)..(at - 60_000)
            "now" -> now in (at - 5 * 60_000)..(at + CALL_REMIND_LATE_MS)
            else -> false
        }
        if (!show) return
        val whenLabel = SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault()).apply { timeZone = IST }.format(Date(at))
        val title = if (phase == "now") {
            context.getString(if (video) R.string.push_call_now_video else R.string.push_call_now)
        } else {
            context.getString(if (video) R.string.push_call_soon_video else R.string.push_call_soon, peer)
        }
        val body = if (phase == "now") context.getString(R.string.push_call_now_body, whenLabel) else whenLabel
        showNotification(context, "callRequests/$id".hashCode(), Channels.CALL, title, body, "callRequests/$id")
    }
}
