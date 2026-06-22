package dev.twango.jetplay

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class JetPlayBundleParityTest {

    private val locales = listOf("de", "es", "fr", "it", "ja", "ko", "pl", "pt_BR", "ru", "zh_CN", "zh_TW")

    @Test
    fun everyLocaleBundleHasExactlyTheBaseKeySet() {
        val baseKeys = loadKeys("/messages/JetPlayBundle.properties")
        val failures = mutableListOf<String>()
        for (locale in locales) {
            val localeKeys = loadKeys("/messages/JetPlayBundle_$locale.properties")
            val missing = baseKeys - localeKeys
            val extra = localeKeys - baseKeys
            if (missing.isNotEmpty() || extra.isNotEmpty()) {
                failures += "$locale missing=$missing extra=$extra"
            }
        }
        assertTrue("Locale bundles diverge from the base key set:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    private fun loadKeys(resource: String): Set<String> {
        val props = Properties()
        javaClass.getResourceAsStream(resource)!!.use { props.load(it) }
        return props.stringPropertyNames().toSet()
    }
}
