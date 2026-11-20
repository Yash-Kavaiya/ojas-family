package com.ojashealing.family.domain

/**
 * Membership plans and what each opens, and when (owner's plan table, 2026-11-20).
 *
 * A free member's time counts from when they joined; a Silver or Gold member's from when their paid
 * plan began (renewals keep it running; a lapse starts it again). A member gets the better of the two.
 * Swamiji and admins have everything. The same table lives in functions/src/access.ts and in
 * firestore.rules (allowed(u, s, g)); keep all three in step.
 */
enum class Plan(val id: String) {
    User("user"), Silver("silver"), Gold("gold");

    companion object {
        fun fromId(id: String?): Plan = entries.firstOrNull { it.id == id } ?: User
    }
}

/** Days on each plan before something opens; null means that plan never gets it. */
data class AccessRule(val user: Int?, val silver: Int?, val gold: Int?) {
    fun daysFor(plan: Plan): Int? = when (plan) {
        Plan.User -> user
        Plan.Silver -> silver
        Plan.Gold -> gold
    }
}

object AccessRules {
    const val WEEK = 7
    const val HALF_YEAR = 182
    const val YEAR = 365

    // Home: Thought of the Day
    val Like = AccessRule(WEEK, 0, 0)
    val ReplyThought = AccessRule(HALF_YEAR, WEEK, WEEK)
    val QuickProtection = AccessRule(HALF_YEAR, 0, 0)

    // Community
    val ReplyAnnouncement = AccessRule(HALF_YEAR, WEEK, 0)
    val ReplyQa = AccessRule(HALF_YEAR, WEEK, WEEK)
    /** A question with words only: one a day. */
    val AskText = AccessRule(HALF_YEAR, WEEK, WEEK)
    /** A question with a photo, audio or video: five a day (ten for Gold after a year). */
    val AskMedia = AccessRule(YEAR, YEAR, HALF_YEAR)
    val ActivityRead = AccessRule(null, HALF_YEAR, WEEK)
    val ActivityReply = AccessRule(null, YEAR, HALF_YEAR)
    val ActivityPost = AccessRule(null, null, YEAR)

    // Practice: Morning and Night
    val DailyPractice = AccessRule(null, WEEK, 0)

    // Profile
    val HealingPhoto = AccessRule(null, YEAR, HALF_YEAR)
    val HideProfile = AccessRule(null, null, YEAR)

    // Paid chat and calls (bought separately, on any plan): with the team from day 1, with Swamiji after 6 months
    val WithSwamiji = AccessRule(HALF_YEAR, HALF_YEAR, HALF_YEAR)
}

private const val DAY_MS = 86_400_000L

/** Whole days from [fromMs] to [nowMs]; 0 when unknown. */
private fun daysBetween(fromMs: Long?, nowMs: Long): Int =
    if (fromMs == null || nowMs < fromMs) 0 else ((nowMs - fromMs) / DAY_MS).toInt()

/** Swamiji and admins: every feature, no limits. */
fun Viewer.hasFullAccess(): Boolean = role == Role.Swamiji || role == Role.Admin

/** The paid plan running now, or [Plan.User]. */
fun Viewer.activePlan(nowMs: Long = System.currentTimeMillis()): Plan =
    if (plan != Plan.User && planUntilMs != null && planUntilMs > nowMs) plan else Plan.User

fun Viewer.daysJoined(nowMs: Long = System.currentTimeMillis()): Int = daysBetween(joinedAtMs, nowMs)

fun Viewer.daysOnPlan(nowMs: Long = System.currentTimeMillis()): Int = daysBetween(planSinceMs, nowMs)

/** True when the table gives this member [rule] now. */
fun Viewer.allows(rule: AccessRule, nowMs: Long = System.currentTimeMillis()): Boolean {
    if (hasFullAccess()) return true
    if (rule.user != null && daysJoined(nowMs) >= rule.user) return true
    val plan = activePlan(nowMs)
    val need = rule.daysFor(plan) ?: return false
    return plan != Plan.User && daysOnPlan(nowMs) >= need
}

/** Days until [rule] opens for this member on their current plan, or null if it never will without another plan. */
fun Viewer.daysUntil(rule: AccessRule, nowMs: Long = System.currentTimeMillis()): Int? {
    if (allows(rule, nowMs)) return 0
    val viaUser = rule.user?.let { it - daysJoined(nowMs) }
    val plan = activePlan(nowMs)
    val viaPlan = if (plan == Plan.User) null else rule.daysFor(plan)?.let { it - daysOnPlan(nowMs) }
    return listOfNotNull(viaUser, viaPlan).minOrNull()?.coerceAtLeast(1)
}

/** One step of a daily limit: from [days] on a plan, [perDay] a day; [Int.MAX_VALUE] means no limit. */
private data class Step(val days: Int, val perDay: Int)

private val AiSteps = mapOf(
    Plan.User to listOf(Step(0, 5), Step(AccessRules.HALF_YEAR, 50), Step(AccessRules.YEAR, 100)),
    Plan.Silver to listOf(Step(0, 50), Step(AccessRules.HALF_YEAR, 100)),
    Plan.Gold to listOf(Step(0, 50), Step(AccessRules.HALF_YEAR, 100), Step(AccessRules.YEAR, Int.MAX_VALUE)),
)

private fun Viewer.bestStep(steps: Map<Plan, List<Step>>, nowMs: Long): Int {
    val free = steps.getValue(Plan.User).lastOrNull { daysJoined(nowMs) >= it.days }?.perDay ?: 0
    val plan = activePlan(nowMs)
    val paid = if (plan == Plan.User) 0 else steps.getValue(plan).lastOrNull { daysOnPlan(nowMs) >= it.days }?.perDay ?: 0
    return maxOf(free, paid)
}

/** Questions to Swamiji AI a day; [Int.MAX_VALUE] for no limit. */
fun Viewer.aiDailyLimit(nowMs: Long = System.currentTimeMillis()): Int =
    if (hasFullAccess()) Int.MAX_VALUE else bestStep(AiSteps, nowMs)

/** Questions a member may post in Q&A today: 1 with words only, 5 once photos, audio and video open, 10 for Gold after a year. */
fun Viewer.qaDailyLimit(nowMs: Long = System.currentTimeMillis()): Int = when {
    hasFullAccess() -> Int.MAX_VALUE
    activePlan(nowMs) == Plan.Gold && daysOnPlan(nowMs) >= AccessRules.YEAR -> 10
    allows(AccessRules.AskMedia, nowMs) -> 5
    allows(AccessRules.AskText, nowMs) -> 1
    else -> 0
}
