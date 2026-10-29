package com.ojashealing.family

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize
import com.ojashealing.family.di.AppContainer

class OjasApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Firebase.initialize(this)
        // Debug builds log a token to register in the Firebase console; release builds use Play Integrity
        Firebase.appCheck.installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
            else PlayIntegrityAppCheckProviderFactory.getInstance()
        )
        container = AppContainer(this)
    }
}
