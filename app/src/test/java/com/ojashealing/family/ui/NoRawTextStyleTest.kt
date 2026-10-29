package com.ojashealing.family.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NoRawTextStyleTest {

    // The coin glyph is drawn at a fixed size inside a fixed-size coin, not body text
    private val allowList = setOf("PaytmCoinAnimation.kt")

    @Test
    fun screensUseThemeTypography() {
        val root = File("src/main/java/com/ojashealing/family/ui")
        val dirs = listOf("screens", "components", "onboarding", "settings").map { File(root, it) }
        val offenders = dirs.filter { it.exists() }
            .flatMap { dir -> dir.walk().filter { it.extension == "kt" && it.name !in allowList }.toList() }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    if (Regex("""fontSize\s*=|fontWeight\s*=""").containsMatchIn(line)) "${file.name}:${i + 1}" else null
                }
            }
        assertTrue("Raw text styles found: $offenders", offenders.isEmpty())
    }
}
