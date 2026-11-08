package com.ojashealing.family

import android.os.Build
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
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
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

    // singleTop: a notification tapped while the app is open arrives here
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DeepLinks.from(intent)
    }
}
