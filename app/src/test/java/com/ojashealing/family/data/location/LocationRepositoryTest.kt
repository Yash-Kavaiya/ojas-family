package com.ojashealing.family.data.location

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationRepositoryTest {

    private val repo = LocationRepository { name -> File("src/main/assets/$name").readText() }

    @Test fun countrySearchIsPrefixAndCaseInsensitive() = assertEquals("IN", repo.countries("ind").first().code)

    @Test fun statesOfIndiaIncludeGujarat() = assertTrue(repo.states("IN", "").any { it.name == "Gujarat" && it.code == "GJ" })

    @Test fun stateSearchFilters() = assertEquals(listOf("Gujarat"), repo.states("IN", "guj").map { it.name })

    @Test fun citiesOfGujaratContainAhmedabad() = assertTrue("Ahmedabad" in repo.cities("IN", "GJ", "ahm"))

    @Test fun citiesForNonIndiaAreEmpty() = assertTrue(repo.cities("US", "CA", "").isEmpty())

    @Test fun indiaHasDialCode91() = assertEquals("91", repo.countries("India").first().dialCode)
}
