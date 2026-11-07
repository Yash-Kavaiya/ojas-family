package com.ojashealing.family.push

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ojashealing.family.MainActivity
import com.ojashealing.family.R

/**
 * Keeps the microphone working while a call is on and the user switches to another app or locks the
 * screen; Android stops background mic access without a foreground service.
 */
class CallService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, startFlags: Int, startId: Int): Int {
        val target = intent?.getStringExtra(EXTRA_TARGET)
        val open = PendingIntent.getActivity(
            this, 9100,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                target?.let { putExtra(EXTRA_TARGET, it) }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, Channels.CALL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.call_ongoing))
            .setContentText(intent?.getStringExtra("name").orEmpty())
            .setOngoing(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, 9100, n, type)
        return START_NOT_STICKY
    }

    companion object {
        fun start(context: Context, callId: String, name: String) {
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, CallService::class.java).putExtra(EXTRA_TARGET, "calls/$callId").putExtra("name", name)
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallService::class.java))
        }
    }
}
