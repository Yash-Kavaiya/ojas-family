package com.ojashealing.family.data.social

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/** How many people use the app and how many of them are members, counted on the server. */
data class UserCounts(val users: Long, val members: Long)

/** The "userCounts" function's answer, or null when it is not the expected shape. */
fun userCountsFrom(data: Any?): UserCounts? {
    val m = data as? Map<*, *> ?: return null
    val users = (m["users"] as? Number)?.toLong() ?: return null
    val members = (m["members"] as? Number)?.toLong() ?: return null
    return UserCounts(users, members)
}

/** Only Swamiji and admins may call it; anyone else gets null. */
suspend fun loadUserCounts(functions: FirebaseFunctions): UserCounts? =
    runCatching { userCountsFrom(functions.getHttpsCallable("userCounts").call().await().getData()) }.getOrNull()
