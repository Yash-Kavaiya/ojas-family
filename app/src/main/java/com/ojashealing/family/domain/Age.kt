package com.ojashealing.family.domain

import java.util.Calendar

// A calendar date without java.time (minSdk 24 has no desugaring here)
data class LocalDateLike(val year: Int, val month: Int, val day: Int) : Comparable<LocalDateLike> {

    override fun compareTo(other: LocalDateLike): Int =
        compareValuesBy(this, other, LocalDateLike::year, LocalDateLike::month, LocalDateLike::day)

    fun iso(): String = "%04d-%02d-%02d".format(year, month, day)

    companion object {
        // Parses "yyyy-MM-dd"; null for anything that is not a real calendar date
        fun parse(iso: String): LocalDateLike? {
            val parts = iso.split("-").mapNotNull { it.toIntOrNull() }
            if (parts.size != 3) return null
            val (y, m, d) = parts
            val cal = Calendar.getInstance().apply {
                isLenient = false
                clear()
                set(y, m - 1, d)
            }
            return runCatching { cal.time; LocalDateLike(y, m, d) }.getOrNull()
        }

        fun today(): LocalDateLike = Calendar.getInstance().let {
            LocalDateLike(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
        }
    }
}

fun ageOn(dob: LocalDateLike, today: LocalDateLike): Int {
    val birthdayPassed = today.month > dob.month || (today.month == dob.month && today.day >= dob.day)
    return (today.year - dob.year - if (birthdayPassed) 0 else 1).coerceAtLeast(0)
}
