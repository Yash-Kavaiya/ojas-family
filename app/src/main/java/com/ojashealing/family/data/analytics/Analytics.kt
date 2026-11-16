package com.ojashealing.family.data.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.ojashealing.family.BuildConfig

/**
 * Google Analytics (GA4, through Firebase), free. It records what is done in the app, never who or what
 * was written: no names, emails, messages, questions or post text, only the kind of action and a few
 * labels such as which practice. The numbers are read in the Firebase console (Analytics).
 *
 * Members can turn it off in Settings → Privacy. Debug builds send nothing unless a developer asks with
 * `adb shell setprop debug.ojas.analytics 1`, so testing never mixes into the real numbers.
 */
object Analytics {
    private const val PREFS = "analytics"
    private const val KEY_ON = "share_usage"
    private const val DEBUG_PROPERTY = "debug.ojas.analytics"

    private var fa: FirebaseAnalytics? = null
    private var lastScreen: String? = null

    /** Call once from Application.onCreate, after Firebase has started. */
    fun init(context: Context) {
        val app = context.applicationContext
        fa = runCatching { FirebaseAnalytics.getInstance(app) }.getOrNull()
        apply(isShared(app))
    }

    /** The member's choice in Settings; on until they turn it off. */
    fun isShared(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, true)

    fun setShared(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
        apply(on)
    }

    private fun apply(shared: Boolean) {
        fa?.setAnalyticsCollectionEnabled(shared && (!BuildConfig.DEBUG || debugAsked()))
    }

    private fun debugAsked(): Boolean = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, DEBUG_PROPERTY) as? String
    }.getOrNull()?.trim() == "1"

    /** A screen became visible; the same screen twice in a row is counted once. */
    fun screen(name: String) {
        if (name.isEmpty() || name == lastScreen) return
        lastScreen = name
        send(AnalyticsEvent(FirebaseAnalytics.Event.SCREEN_VIEW, mapOf(FirebaseAnalytics.Param.SCREEN_NAME to name, FirebaseAnalytics.Param.SCREEN_CLASS to name)))
    }

    fun practiceStart(section: String) = send(Events.practice("practice_start", section))
    fun practiceComplete(section: String) = send(Events.practice("practice_complete", section))
    fun aiQuestion(withPhoto: Boolean) = send(AnalyticsEvent("ai_question", mapOf("with_photo" to if (withPhoto) "yes" else "no")))
    fun aiRating(helpful: Boolean) = send(AnalyticsEvent("ai_rating", mapOf("helpful" to if (helpful) "yes" else "no")))
    fun post(kind: String) = send(Events.kind("post_create", kind))
    fun like(kind: String) = send(Events.kind("like", kind))
    fun notificationOpen(group: String) = send(Events.kind("notification_open", group))
    fun callStart(video: Boolean) = send(AnalyticsEvent("call_start", mapOf("video" to if (video) "yes" else "no")))
    fun pointsGift() = send(AnalyticsEvent("points_transfer", emptyMap()))

    /** Who is using the app, in broad groups only: language, levels and whether they are paid. */
    fun setMember(language: String, userLevel: Int, memberLevel: String, paid: Boolean) {
        val f = fa ?: return
        f.setUserProperty("app_language", language.take(10))
        f.setUserProperty("user_level", userLevel.toString())
        f.setUserProperty("member_level", memberLevel.lowercase().take(20))
        f.setUserProperty("paid", if (paid) "yes" else "no")
    }

    private fun send(e: AnalyticsEvent) {
        val f = fa ?: return
        runCatching { f.logEvent(e.name, Bundle().apply { e.params.forEach { (k, v) -> putString(k, v) } }) }
    }
}

/** One event as Google Analytics receives it. */
data class AnalyticsEvent(val name: String, val params: Map<String, String>)

/** Builds the events, kept apart from Firebase so they can be checked in unit tests. */
object Events {
    /** Google Analytics takes names of up to 40 letters, digits and underscores, starting with a letter. */
    val NAME = Regex("^[a-zA-Z][a-zA-Z0-9_]{0,39}$")

    fun practice(name: String, section: String) = AnalyticsEvent(name, mapOf("section" to clean(section)))
    fun kind(name: String, kind: String) = AnalyticsEvent(name, mapOf("kind" to clean(kind)))

    /**
     * A screen's name from its navigation route, e.g. "com.x.ui.main.QuestionRoute/{id}" -> "Question".
     * The tabs screen is named after the open tab instead.
     */
    fun screenName(route: String?, tab: String): String {
        val base = route.orEmpty().substringBefore('/').substringBefore('?').substringAfterLast('.').removeSuffix("Route")
        return if (base.isEmpty() || base == "Tabs") tab else base
    }

    /** Labels are short words, never free text. */
    private fun clean(v: String) = v.lowercase().replace(Regex("[^a-z0-9_]"), "_").take(36)
}
