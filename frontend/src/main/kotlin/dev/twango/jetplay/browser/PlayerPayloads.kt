package dev.twango.jetplay.browser

import dev.twango.jetplay.media.MediaInfo
import dev.twango.jetplay.media.Spectrogram
import java.util.Base64

// Hand-rolled JS/JSON encoders for the JCEF bridge payloads.
internal object PlayerPayloads {
    private const val HEX_RADIX = 16
    private const val UNICODE_ESCAPE_HEX_LENGTH = 4

    fun escapeJs(s: String): String = s.replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")
        .replace("<", "\\x3c")
        .replace(">", "\\x3e")

    // Strict JSON, not spliced: it carries arbitrary tag text and a base64 art URL.
    internal fun mediaInfoJson(info: MediaInfo): String? {
        val parts = buildList {
            info.codec?.let { add("\"codec\":${jsonString(it)}") }
            info.container?.let { add("\"container\":${jsonString(it)}") }
            info.sampleRateHz?.let { add("\"sampleRateHz\":$it") }
            info.channels?.let { add("\"channels\":$it") }
            info.channelLabel?.let { add("\"channelLabel\":${jsonString(it)}") }
            info.bitDepth?.let { add("\"bitDepth\":${jsonString(it)}") }
            info.bitrateBps?.let { add("\"bitrateBps\":$it") }
            info.durationMs?.let { add("\"durationMs\":$it") }
            info.sizeBytes?.let { add("\"sizeBytes\":$it") }
            info.width?.let { add("\"width\":$it") }
            info.height?.let { add("\"height\":$it") }
            info.frameRate?.let { add("\"frameRate\":$it") }
            info.videoCodec?.let { add("\"videoCodec\":${jsonString(it)}") }
            info.pixelFormat?.let { add("\"pixelFormat\":${jsonString(it)}") }
            info.videoBitrateBps?.let { add("\"videoBitrateBps\":$it") }
            if (info.tags.isNotEmpty()) {
                val arr = info.tags.joinToString(",", "[", "]") { tag ->
                    "{\"label\":${jsonString(tag.label)},\"value\":${jsonString(tag.value)}}"
                }
                add("\"tags\":$arr")
            }
            info.albumArt?.let { add("\"albumArt\":${jsonString(it)}") }
        }
        if (parts.isEmpty()) return null
        return parts.joinToString(",", "{", "}")
    }

    // Numbers are emitted as literals; only the base64 matrix is a string (it never contains JS specials).
    internal fun spectrogramJson(spec: Spectrogram?): String {
        if (spec == null) return "{\"ok\":false}"
        val data = Base64.getEncoder().encodeToString(spec.magnitudes)
        return buildString {
            append("{\"ok\":true")
            append(",\"timeCols\":${spec.timeCols}")
            append(",\"freqBins\":${spec.freqBins}")
            append(",\"durationMs\":${spec.durationMs}")
            append(",\"sampleRateHz\":${spec.sampleRateHz}")
            append(",\"minHz\":${spec.minHz}")
            append(",\"maxHz\":${spec.maxHz}")
            append(",\"dbFloor\":${spec.dbFloor}")
            append(",\"dbCeil\":${spec.dbCeil}")
            append(",\"logFreq\":${spec.logFreq}")
            append(",\"data\":${jsonString(data)}")
            append("}")
        }
    }

    internal fun jsonString(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")

                '\\' -> sb.append("\\\\")

                '\n' -> sb.append("\\n")

                '\r' -> sb.append("\\r")

                '\t' -> sb.append("\\t")

                '\b' -> sb.append("\\b")

                '\u000C' -> sb.append("\\f")

                // Valid in JSON but terminate a JS string literal.
                '\u2028' -> sb.append("\\u2028")

                '\u2029' -> sb.append("\\u2029")

                else -> if (c < ' ') {
                    sb.append("\\u").append(c.code.toString(HEX_RADIX).padStart(UNICODE_ESCAPE_HEX_LENGTH, '0'))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
