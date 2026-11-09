package dev.twango.jetplay.browser

import dev.twango.jetplay.media.MediaInfo
import dev.twango.jetplay.media.MediaTag
import dev.twango.jetplay.media.Spectrogram
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerBridgeEscapeTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun javascriptStringRoundTripPreservesFileNamesAndControlCharacters() {
        val original = "it's </script> \"test\"\nwith\\stuff\r\t\u0001\u2028\u2029"
        val encoded = PlayerPayloads.jsonString(original)
        assertEquals(original, Json.decodeFromString<String>(encoded))
        assertFalse(encoded.contains('<'))
    }

    @Test
    fun metadataRoundTripIncludesAllSerializableFields() {
        val info = MediaInfo(
            codec = "mp3",
            container = "mp3",
            sampleRateHz = 44100,
            channels = 2,
            channelLabel = "stereo",
            bitDepth = null,
            bitrateBps = 320000,
            durationMs = 1000,
            sizeBytes = 40000,
            width = 1920,
            height = 1080,
            frameRate = 29.97,
            videoCodec = "vp9",
            pixelFormat = "yuv420p",
            videoBitrateBps = 2000000,
            tags = listOf(MediaTag("Title", "O'Brien \"x\"\r\n</script>"), MediaTag("Artist", "A\\B")),
            albumArt = "data:image/png;base64,AAAA",
        )
        val encoded = requireNotNull(PlayerPayloads.mediaInfoJson(info))
        assertEquals(info, json.decodeFromString<MediaInfo>(encoded))
        assertFalse(Json.parseToJsonElement(encoded).jsonObject.containsKey("bitDepth"))
        assertFalse(encoded.contains('<'))
    }

    @Test
    fun emptyMetadataDoesNotOpenAnEmptyInspector() {
        assertNull(PlayerPayloads.mediaInfoJson(MediaInfo(null, null, null, null, null, null, null, null, null)))
    }

    @Test
    fun unavailableSpectrogramPreservesItsWireContract() {
        val payload = Json.parseToJsonElement(PlayerPayloads.spectrogramJson(null)).jsonObject
        assertEquals(setOf("ok"), payload.keys)
        assertFalse(payload.getValue("ok").jsonPrimitive.boolean)
    }

    @Test
    fun spectrogramUsesBase64BytesAndNamedMetadata() {
        val spec = Spectrogram(2, 2, 1000, 44100, 20, 20000, -80f, 0f, true, byteArrayOf(0, 127, -1, 64))
        val payload = Json.parseToJsonElement(PlayerPayloads.spectrogramJson(spec)).jsonObject
        assertEquals(true, payload.getValue("ok").jsonPrimitive.boolean)
        assertEquals(spec.timeCols, payload.getValue("timeCols").jsonPrimitive.int)
        assertEquals(spec.freqBins, payload.getValue("freqBins").jsonPrimitive.int)
        assertEquals("AH//QA==", payload.getValue("data").jsonPrimitive.content)
        assertFalse(payload.containsKey("magnitudes"))
    }
}
