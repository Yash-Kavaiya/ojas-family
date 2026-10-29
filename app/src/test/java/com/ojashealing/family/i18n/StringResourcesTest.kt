package com.ojashealing.family.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class StringResourcesTest {

    private fun strings(dir: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$dir/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate {
            val el = nodes.item(it)
            el.attributes.getNamedItem("name").nodeValue to el.textContent
        }
    }

    private val placeholder = Regex("""%\d+\$[sd]""")

    @Test
    fun everyKeyTranslated() {
        val en = strings("values").keys
        assertEquals(en - "app_name", strings("values-hi").keys - "app_name")
        assertEquals(en - "app_name", strings("values-gu").keys - "app_name")
    }

    @Test
    fun placeholdersMatch() {
        val en = strings("values")
        for (lang in listOf("values-hi", "values-gu")) {
            strings(lang).forEach { (key, value) ->
                assertEquals("$lang/$key", placeholder.findAll(en.getValue(key)).map { it.value }.toSet(),
                    placeholder.findAll(value).map { it.value }.toSet())
            }
        }
    }
}
