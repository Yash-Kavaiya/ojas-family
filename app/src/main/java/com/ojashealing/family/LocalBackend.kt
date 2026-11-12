package com.ojashealing.family

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import com.google.firebase.storage.storage

/**
 * Debug builds only: runs the whole app against the Firebase emulators on a developer's computer, so
 * sign-in, posts, chat, admin tools and Cloud Functions can be tried end to end without touching the
 * live project.
 *
 *     cd firebase && firebase emulators:start --project demo-ojas
 *     adb shell setprop debug.ojas.emulator 10.0.2.2     (10.0.2.2 is "this computer" from the Android emulator)
 *     adb shell am force-stop com.ojashealing.family     (then open the app)
 *
 * Clear it with `adb shell setprop debug.ojas.emulator ""`. The project id becomes "demo-ojas": the
 * emulators treat "demo-" projects as local only, so no push, payment or data ever reaches production.
 * Release builds never read the property.
 */
object LocalBackend {
    private const val PROPERTY = "debug.ojas.emulator"
    const val PROJECT = "demo-ojas"

    /** The emulator host a developer asked for, or null (always null in release builds). */
    fun hostOrNull(): String? {
        if (!BuildConfig.DEBUG) return null
        return runCatching {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, PROPERTY) as? String
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Call first thing in Application.onCreate, before anything else touches Firebase. Starts the
     * default app on the emulators and returns true, or returns false to start it normally. Debug
     * builds don't auto-start Firebase (src/debug/AndroidManifest.xml), so nothing is deleted here.
     */
    fun initializeIfAsked(context: Context): Boolean {
        val host = hostOrNull() ?: return false
        val base = FirebaseOptions.fromResource(context) ?: return false
        if (FirebaseApp.getApps(context).isNotEmpty()) return false
        FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder(base).setProjectId(PROJECT).setStorageBucket("$PROJECT.appspot.com").build()
        )
        Firebase.auth.useEmulator(host, 9099)
        Firebase.firestore.useEmulator(host, 8080)
        Firebase.storage.useEmulator(host, 9199)
        Firebase.functions("asia-south1").useEmulator(host, 5001)
        Log.w("OjasLocalBackend", "Using Firebase emulators at $host (project $PROJECT)")
        return true
    }
}
