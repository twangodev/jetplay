package dev.twango.jetplay.media

import org.junit.Assert.assertTrue
import org.junit.Test

class RawAudioRegistrationTest {

    @Test
    fun rawAudioHintsAreRegisteredInTheFrontendDescriptor() {
        val xml = javaClass.getResource("/dev.twango.jetplay.frontend.xml")!!.readText()
        val registered = Regex("""extensions\s*=\s*"([^"]*)"""").find(xml)
            ?.groupValues?.get(1)
            ?.split(";")
            ?.map { it.trim().lowercase() }
            ?.filterTo(mutableSetOf()) { it.isNotEmpty() }
            ?: error("Could not find a fileType extensions attribute in the frontend descriptor")
        val missing = MediaClassification.rawAudioExtensions - registered
        assertTrue("raw-audio extensions missing from the frontend descriptor: $missing", missing.isEmpty())
    }
}
