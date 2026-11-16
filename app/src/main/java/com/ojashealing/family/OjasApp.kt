package com.ojashealing.family

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.initialize
import com.ojashealing.family.data.auth.signingSha1
import com.ojashealing.family.di.AppContainer
import com.ojashealing.family.push.Channels

class OjasApp : Application(), coil.ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Debug builds can be pointed at local Firebase emulators (see LocalBackend); otherwise the live project
        if (!LocalBackend.initializeIfAsked(this)) Firebase.initialize(this)
        // Sign-in failures from an APK signed with an unregistered key look like Firebase errors
        // (MISSING_CLIENT_IDENTIFIER, DEVELOPER_ERROR); tagging every report with the key makes them obvious
        runCatching { FirebaseCrashlytics.getInstance().setCustomKey("signing_sha1", signingSha1(this) ?: "unknown") }
        // Debug builds log a token to register in the Firebase console; release builds use Play Integrity
        Firebase.appCheck.installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
            else PlayIntegrityAppCheckProviderFactory.getInstance()
        )
        container = AppContainer(this)
        // Google Analytics: what is done in the app, never who or what was written (see Analytics)
        com.ojashealing.family.data.analytics.Analytics.init(this)
        Channels.create(this)
    }

    /** One image loader for the whole app: pictures fade in over 200ms instead of popping in. */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this).crossfade(200).build()
}
