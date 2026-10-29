package com.ojashealing.family.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AgeTest {
    @Test fun dayBeforeBirthday() = assertEquals(35, ageOn(LocalDateLike(1990, 6, 15), LocalDateLike(2026, 6, 14)))
    @Test fun onBirthday() = assertEquals(36, ageOn(LocalDateLike(1990, 6, 15), LocalDateLike(2026, 6, 15)))
    @Test fun leapDayBirthdayBeforeMarch() = assertEquals(25, ageOn(LocalDateLike(2000, 2, 29), LocalDateLike(2026, 2, 28)))
    @Test fun parsesIsoDate() = assertEquals(LocalDateLike(1997, 4, 18), LocalDateLike.parse("1997-04-18"))
    @Test fun rejectsBadDate() = assertEquals(null, LocalDateLike.parse("1997-13-40"))
}
