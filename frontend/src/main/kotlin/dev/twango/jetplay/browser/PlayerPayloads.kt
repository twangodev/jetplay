package dev.twango.jetplay.browser

import dev.twango.jetplay.media.MediaInfo
import dev.twango.jetplay.media.Spectrogram
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

internal object PlayerPayloads {
    private val json = Json { explicitNulls = false }
    private val configCodec = Json(json) { encodeDefaults = true }

    fun configJson(config: PlayerConfig): String {
        // Optional translated copy must fall back to the browser's labels when unset.
        val normalized = config.copy(
            errorMessage = config.errorMessage?.takeIf { it.isNotEmpty() },
            transcodingReason = config.transcodingReason?.takeIf { it.isNotEmpty() },
            ui = config.ui.copy(
                transcodingLabel = config.ui.transcodingLabel?.takeIf { it.isNotEmpty() },
                transcodingTip = config.ui.transcodingTip?.takeIf { it.isNotEmpty() },
                errorTitle = config.ui.errorTitle?.takeIf { it.isNotEmpty() },
            ),
        )
        return scriptSafe(configCodec.encodeToString(normalized))
    }

    fun jsonString(value: String): String = scriptSafe(json.encodeToString(value))

    fun mediaInfoJson(info: MediaInfo): String? = json.encodeToString(info).takeUnless { it == "{}" }?.let(::scriptSafe)

    fun spectrogramJson(spec: Spectrogram?): String = scriptSafe(
        if (spec == null) {
            json.encodeToString(UnavailableSpectrogram(ok = false))
        } else {
            json.encodeToString(
                SpectrogramPayload(
                    ok = true,
                    timeCols = spec.timeCols,
                    freqBins = spec.freqBins,
                    durationMs = spec.durationMs,
                    sampleRateHz = spec.sampleRateHz,
                    minHz = spec.minHz,
                    maxHz = spec.maxHz,
                    dbFloor = spec.dbFloor,
                    dbCeil = spec.dbCeil,
                    logFreq = spec.logFreq,
                    data = Base64.getEncoder().encodeToString(spec.magnitudes),
                ),
            )
        },
    )

    // HTML recognizes a closing script tag even inside a JSON string literal.
    private fun scriptSafe(value: String): String = value.replace("<", "\\u003c")
}

@Serializable
private data class UnavailableSpectrogram(val ok: Boolean)

@Serializable
private data class SpectrogramPayload(
    val ok: Boolean,
    val timeCols: Int,
    val freqBins: Int,
    val durationMs: Long,
    val sampleRateHz: Int,
    val minHz: Int,
    val maxHz: Int,
    val dbFloor: Float,
    val dbCeil: Float,
    val logFreq: Boolean,
    val data: String,
)
