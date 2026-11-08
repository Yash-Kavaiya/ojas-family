package com.ojashealing.family

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojashealing.family.data.settings.ThemeMode
import android.content.Intent
import com.ojashealing.family.push.DeepLinks
import com.ojashealing.family.ui.navigation.OjasRoot
import com.ojashealing.family.ui.theme.OjasFamilyTheme

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreenForCall(intent)
        DeepLinks.from(intent)
        val settings = (application as OjasApp).container.settings

        setContent {
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.System)
            val dark = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            OjasFamilyTheme(darkTheme = dark) {
                OjasRoot()
            }
        }
    }

    /**
     * A ringing call opened from its full-screen notification shows over the lock screen and wakes it.
     * Only for calls: otherwise the app stays behind the lock screen. The call screen turns it off again.
     */
    private fun showOverLockScreenForCall(intent: Intent?) {
        if (intent?.getStringExtra(com.ojashealing.family.push.EXTRA_TARGET)?.startsWith("calls/") != true) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else @Suppress("DEPRECATION") window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
    }

    /** Before Android 12, leaving the app during a video call shrinks it into picture-in-picture by hand. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT !in android.os.Build.VERSION_CODES.O until android.os.Build.VERSION_CODES.S) return
        val call = (application as OjasApp).container.callManager.ui.value ?: return
        if (call.video && (call.phase == com.ojashealing.family.data.call.CallPhase.Connected || call.phase == com.ojashealing.family.data.call.CallPhase.Reconnecting)) {
            runCatching { enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(9, 16)).build()) }
        }
    }

    // singleTop: a notification tapped while the app is open arrives here
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showOverLockScreenForCall(intent)
        DeepLinks.from(intent)
    }
}
