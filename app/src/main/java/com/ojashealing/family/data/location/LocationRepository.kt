package com.ojashealing.family.data.location

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Country(@SerialName("c") val code: String, @SerialName("n") val name: String, @SerialName("p") val dialCode: String)

@Serializable
data class Region(@SerialName("c") val code: String, @SerialName("n") val name: String)

/**
 * Offline country -> state -> city lookup from bundled assets (countries-states-cities-database, ODbL).
 * Only India has a city list; elsewhere the city field is free text.
 */
class LocationRepository(private val readAsset: (String) -> String) {

    private val json = Json { ignoreUnknownKeys = true }

    private val countries: List<Country> by lazy { json.decodeFromString(readAsset("location/countries.json")) }
    private val states: Map<String, List<Region>> by lazy { json.decodeFromString(readAsset("location/states.json")) }
    private val indiaCities: Map<String, List<String>> by lazy { json.decodeFromString(readAsset("location/in_cities.json")) }

    fun countries(query: String): List<Country> = countries.search(query) { it.name }

    fun country(code: String): Country? = countries.firstOrNull { it.code == code }

    fun states(countryCode: String, query: String): List<Region> = states[countryCode].orEmpty().search(query) { it.name }

    fun cities(countryCode: String, stateCode: String, query: String): List<String> =
        if (countryCode != "IN") emptyList() else indiaCities[stateCode].orEmpty().search(query) { it }

    // Names starting with the query first, then names containing it; empty query keeps the full list
    private fun <T> List<T>.search(query: String, name: (T) -> String): List<T> {
        val q = query.trim()
        if (q.isEmpty()) return this
        val (prefix, rest) = filter { name(it).contains(q, ignoreCase = true) }
            .partition { name(it).startsWith(q, ignoreCase = true) }
        return prefix + rest
    }
}
