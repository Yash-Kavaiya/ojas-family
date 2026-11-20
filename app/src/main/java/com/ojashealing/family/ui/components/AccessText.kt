package com.ojashealing.family.ui.components

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.ojashealing.family.R
import com.ojashealing.family.domain.AccessRule
import com.ojashealing.family.domain.AccessRules
import com.ojashealing.family.domain.Plan

/** "Free after 6 months · Silver and Gold from day 1": who gets [rule], and when, in the member's language. */
@Composable
fun accessRuleText(rule: AccessRule): String {
    LocalConfiguration.current // read again when the language changes
    return describeRule(LocalContext.current.resources, rule)
}

fun describeRule(res: Resources, rule: AccessRule): String {
    // Plans that open it after the same time are named together, in plan order
    val groups = LinkedHashMap<Int, MutableList<Plan>>()
    Plan.entries.forEach { p -> rule.daysFor(p)?.let { groups.getOrPut(it) { mutableListOf() }.add(p) } }
    return groups.entries.joinToString(" · ") { (days, plans) ->
        val names = plans.map { res.getString(planName(it)) }
        val who = if (names.size == 1) names[0] else res.getString(R.string.access_and, names.dropLast(1).joinToString(", "), names.last())
        res.getString(R.string.access_line, who, whenText(res, days))
    }
}

fun planName(plan: Plan): Int = when (plan) {
    Plan.User -> R.string.plan_name_user
    Plan.Silver -> R.string.plan_name_silver
    Plan.Gold -> R.string.plan_name_gold
}

private fun whenText(res: Resources, days: Int): String = when (days) {
    0 -> res.getString(R.string.access_from_day_one)
    AccessRules.HALF_YEAR -> res.getString(R.string.access_after_months, 6)
    AccessRules.YEAR -> res.getString(R.string.access_after_months, 12)
    else -> res.getString(R.string.access_after_days, days)
}
