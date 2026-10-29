package com.ojashealing.family

import com.ojashealing.family.data.model.UserProfile
import com.ojashealing.family.data.repository.OjasRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class UserProfileTest {

    private fun dobYearsAgo(years: Int, dayShift: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.YEAR, -years)
        cal.add(Calendar.DAY_OF_MONTH, dayShift)
        return SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(cal.time)
    }

    @Test
    fun age_countsBirthdayAlreadyPassedThisYear() {
        assertEquals(30, UserProfile(dob = dobYearsAgo(30, dayShift = -1)).age)
    }

    @Test
    fun age_isOneLessBeforeBirthday() {
        // On 31 Dec, "tomorrow" rolls into next year and the case no longer applies
        val now = Calendar.getInstance()
        org.junit.Assume.assumeFalse(now.get(Calendar.MONTH) == Calendar.DECEMBER && now.get(Calendar.DAY_OF_MONTH) == 31)
        assertEquals(29, UserProfile(dob = dobYearsAgo(30, dayShift = 1)).age)
    }

    @Test
    fun age_isZeroForInvalidDob() {
        assertEquals(0, UserProfile(dob = "not-a-date").age)
    }

    @Test
    fun dailyLoginReward_isGrantedOncePerRealDay() {
        val repo = OjasRepository()
        val before = repo.userProfile.value.mokshaPoints

        repo.checkDailyLoginReward()
        repo.checkDailyLoginReward()

        assertEquals(before + 1, repo.userProfile.value.mokshaPoints)
    }
}
