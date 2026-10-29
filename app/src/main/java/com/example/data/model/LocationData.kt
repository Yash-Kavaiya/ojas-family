package com.example.data.model

object LocationDirectory {
    val countries = listOf(
        "India",
        "United States",
        "United Kingdom",
        "Canada",
        "Australia",
        "Germany",
        "United Arab Emirates",
        "Singapore",
        "New Zealand",
        "Mauritius",
        "South Africa",
        "Kenya"
    )

    val statesByCountry = mapOf(
        "India" to listOf(
            "Gujarat",
            "Maharashtra",
            "Rajasthan",
            "Karnataka",
            "Delhi",
            "Tamil Nadu",
            "Uttar Pradesh",
            "Punjab",
            "Madhya Pradesh",
            "Haryana",
            "West Bengal",
            "Kerala",
            "Telangana",
            "Andhra Pradesh"
        ),
        "United States" to listOf(
            "California",
            "Texas",
            "New York",
            "New Jersey",
            "Illinois",
            "Washington",
            "Florida",
            "Georgia"
        ),
        "United Kingdom" to listOf(
            "England",
            "Scotland",
            "Wales",
            "Northern Ireland"
        ),
        "Canada" to listOf(
            "Ontario",
            "British Columbia",
            "Alberta",
            "Quebec"
        ),
        "Australia" to listOf(
            "New South Wales",
            "Victoria",
            "Queensland",
            "Western Australia"
        )
    )

    val citiesByState = mapOf(
        "Gujarat" to listOf(
            "Ahmedabad",
            "Surat",
            "Vadodara",
            "Rajkot",
            "Bhavnagar",
            "Jamnagar",
            "Gandhinagar",
            "Junagadh",
            "Anand",
            "Navsari",
            "Morbi",
            "Bharuch",
            "Porbandar",
            "Mehsana",
            "Bhuj",
            "Valsad",
            "Amreli",
            "Patan",
            "Palanpur",
            "Botad",
            "Gondal",
            "Veraval"
        ),
        "Maharashtra" to listOf(
            "Mumbai",
            "Pune",
            "Nagpur",
            "Thane",
            "Nashik",
            "Aurangabad",
            "Solapur",
            "Kolhapur"
        ),
        "Rajasthan" to listOf(
            "Jaipur",
            "Jodhpur",
            "Udaipur",
            "Kota",
            "Ajmer",
            "Bikaner"
        ),
        "Karnataka" to listOf(
            "Bengaluru",
            "Mysuru",
            "Hubballi",
            "Mangaluru",
            "Belagavi"
        ),
        "Delhi" to listOf(
            "New Delhi",
            "Central Delhi",
            "South Delhi",
            "West Delhi",
            "East Delhi"
        ),
        "California" to listOf(
            "San Francisco",
            "San Jose",
            "Los Angeles",
            "San Diego",
            "Fremont",
            "Sunnyvale"
        ),
        "New Jersey" to listOf(
            "Edison",
            "Jersey City",
            "Princeton",
            "Newark"
        ),
        "Texas" to listOf(
            "Austin",
            "Dallas",
            "Houston",
            "Plano",
            "Irving"
        ),
        "England" to listOf(
            "London",
            "Leicester",
            "Birmingham",
            "Manchester",
            "Wembley"
        ),
        "Ontario" to listOf(
            "Toronto",
            "Mississauga",
            "Brampton",
            "Ottawa"
        )
    )

    fun getStatesForCountry(country: String): List<String> {
        return statesByCountry[country] ?: listOf("General Province / State", "Capital Region", "Metro Area")
    }

    fun getCitiesForState(state: String): List<String> {
        return citiesByState[state] ?: listOf("Central City", "Metro Town", "East District", "West District", "Village Center")
    }
}
