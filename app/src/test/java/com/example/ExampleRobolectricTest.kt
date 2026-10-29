package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.UserLevel
import com.example.data.repository.OjasRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Ojas Family", appName)
  }

  @Test
  fun `test Moksha points on current date like earns point`() {
    val repository = OjasRepository()
    val initialPoints = repository.userProfile.value.mokshaPoints
    // Like today's thought (t_0)
    val success = repository.toggleLikeThought("t_0")
    assertTrue(success)
    val newPoints = repository.userProfile.value.mokshaPoints
    assertEquals(initialPoints + 1, newPoints)
  }

  @Test
  fun `test user level permission rules`() {
    assertTrue(UserLevel.LEVEL_2.canLike())
    assertTrue(!UserLevel.LEVEL_1.canLike())
    assertTrue(UserLevel.LEVEL_3.canComment())
    assertTrue(UserLevel.LEVEL_3.canAskQuestion())
    assertTrue(UserLevel.LEVEL_4.canAccessDailyPractices())
  }
}
