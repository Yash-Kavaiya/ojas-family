package com.ojashealing.family.push

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ojashealing.family.R
import java.util.Calendar

/** "HH:mm" -> the next time it occurs after [now] (today if still ahead, else tomorrow). */
fun nextOccurrence(hhmm: String, now: Calendar): Calendar? {
    val parts = hhmm.split(":").mapNotNull { it.toIntOrNull() }
    if (parts.size != 2 || parts[0] !in 0..23 || parts[1] !in 0..59) return null
    return (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, parts[0]); set(Calendar.MINUTE, parts[1]); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
    }
}

/**
 * Morning and night practice reminders (spec 2.3, 2.4) at the times chosen in Settings. Uses inexact
 * "allow while idle" alarms: they arrive within a few minutes without the exact-alarm permission.
 */
object Reminders {
    private const val PREFS = "reminders"
    const val MORNING = "morning"
    const val NIGHT = "night"

    private fun intent(context: Context, kind: String) = PendingIntent.getBroadcast(
        context, kind.hashCode(),
        Intent(context, ReminderReceiver::class.java).putExtra("kind", kind),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** [enabled] false (practices locked, or signed out) cancels both. Remembered for reboot. */
    fun schedule(context: Context, morning: String, night: String, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(MORNING, morning).putString(NIGHT, night).putBoolean("enabled", enabled).apply()
        val am = context.getSystemService(AlarmManager::class.java)
        for ((kind, time) in listOf(MORNING to morning, NIGHT to night)) {
            val pi = intent(context, kind)
            am.cancel(pi)
            if (!enabled) continue
            val at = nextOccurrence(time, Calendar.getInstance()) ?: continue
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.timeInMillis, pi)
        }
    }

    /** After a reboot, or after a reminder fired, set the next ones from the saved times. */
    fun reschedule(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        schedule(context, p.getString(MORNING, "06:00")!!, p.getString(NIGHT, "21:30")!!, p.getBoolean("enabled", false))
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val morning = intent.getStringExtra("kind") == Reminders.MORNING
        showNotification(
            context,
            id = if (morning) 7001 else 7002,
            channel = Channels.PRACTICE,
            title = context.getString(if (morning) R.string.reminder_morning_title else R.string.reminder_night_title),
            body = context.getString(if (morning) R.string.reminder_morning_body else R.string.reminder_night_body),
            target = if (morning) "practice/morning" else "practice/night"
        )
        Reminders.reschedule(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) Reminders.reschedule(context)
    }
}
