package com.ojashealing.family.data.about

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class AboutLink(val title: String, val url: String)

/**
 * The Home tab's About box, from config/about (admins edit it on the admin website).
 * Empty text means "use the app's built-in wording".
 */
data class AboutInfo(
    val ojasFamily: String = "",
    val swamiji: String = "",
    val links: List<AboutLink> = emptyList(),
    /** App help: how to use the app. */
    val help: String = "",
    /** Legal: the disclaimer. */
    val legal: String = ""
)

const val OJAS_YOUTUBE = "https://www.youtube.com/@OjasHealing"

/** Ojas Healing's own pages, shown as icons at the bottom of Home. A link of the same platform set in About replaces one. */
val OJAS_SOCIAL: List<String> = listOf(
    "https://whatsapp.com/channel/0029Va5vLlx2ER6oRfdZ2d3Q",
    OJAS_YOUTUBE,
    "https://www.facebook.com/OjasHealing/",
    "https://www.instagram.com/ojas_healing/",
    "https://www.threads.net/@ojas_healing",
    "https://t.me/OjasHealing",
    "https://www.reddit.com/r/OjasHealing/",
    "https://ojas-healing.quora.com/",
)

/** Text in the app language: "ojasFamily_gu" / "ojasFamily_hi" when set, else the plain field. */
fun DocumentSnapshot.toAbout(language: String): AboutInfo {
    fun text(field: String) = getString("${field}_$language")?.takeIf { it.isNotBlank() } ?: getString(field).orEmpty()
    val links = (get("links") as? List<*>).orEmpty().mapNotNull { l ->
        val m = l as? Map<*, *> ?: return@mapNotNull null
        val url = (m["url"] as? String)?.trim().orEmpty()
        val title = (m["title"] as? String)?.trim().orEmpty()
        if (url.startsWith("https://") && title.isNotEmpty()) AboutLink(title, url) else null
    }
    return AboutInfo(text("ojasFamily"), text("swamiji"), links, text("help"), text("legal"))
}

class AboutRepository(private val db: FirebaseFirestore) {
    fun observe(language: String): Flow<AboutInfo> = callbackFlow {
        trySend(AboutInfo())
        val reg = db.document("config/about").addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists()) trySend(snap.toAbout(language))
        }
        awaitClose { reg.remove() }
    }
}
