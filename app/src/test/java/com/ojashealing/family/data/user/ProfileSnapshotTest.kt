package com.ojashealing.family.data.user

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSnapshotTest {
    // An empty offline cache says "no document" even when the profile exists on the server
    @Test fun missingDocFromCacheIsNotAnAnswer() = assertFalse(isDefinitive(exists = false, fromCache = true))
    @Test fun missingDocFromServerIsAnAnswer() = assertTrue(isDefinitive(exists = false, fromCache = false))
    @Test fun cachedDocIsAnAnswer() = assertTrue(isDefinitive(exists = true, fromCache = true))
}
