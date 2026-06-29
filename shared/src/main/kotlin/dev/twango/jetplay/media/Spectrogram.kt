package dev.twango.jetplay.media

import kotlinx.serialization.Serializable

/**
 * A precomputed STFT magnitude heatmap. [magnitudes] is a column-major matrix of
 * unsigned bytes: cell `(col, bin)` lives at `col * freqBins + bin`, bin 0 = lowest frequency.
 * Cell value `b` maps to `dbFloor + (b / 255) * (dbCeil - dbFloor)` dBFS.
 */
@Serializable
data class Spectrogram(
    /** Matrix width: time frames after pooling. */
    val timeCols: Int,
    /** Matrix height: frequency bins. */
    val freqBins: Int,
    /** Audio span the columns cover; column `c` covers `[c, c+1) / timeCols * durationMs`. */
    val durationMs: Long,
    val sampleRateHz: Int,
    /** Bottom of the frequency axis. */
    val minHz: Int,
    /** Top of the frequency axis: `min(Nyquist, 20000)`. */
    val maxHz: Int,
    val dbFloor: Float,
    val dbCeil: Float,
    /** True when bins are log-spaced between [minHz] and [maxHz] backend-side. */
    val logFreq: Boolean,
    /** Column-major magnitudes, length `timeCols * freqBins`, each an unsigned 0..255. */
    val magnitudes: ByteArray,
) {
    override fun equals(other: Any?) = this === other || (
        other is Spectrogram &&
            timeCols == other.timeCols &&
            freqBins == other.freqBins &&
            durationMs == other.durationMs &&
            sampleRateHz == other.sampleRateHz &&
            minHz == other.minHz &&
            maxHz == other.maxHz &&
            dbFloor == other.dbFloor &&
            dbCeil == other.dbCeil &&
            logFreq == other.logFreq &&
            magnitudes.contentEquals(other.magnitudes)
        )

    override fun hashCode(): Int {
        var result = timeCols
        result = 31 * result + freqBins
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + sampleRateHz
        result = 31 * result + minHz
        result = 31 * result + maxHz
        result = 31 * result + dbFloor.hashCode()
        result = 31 * result + dbCeil.hashCode()
        result = 31 * result + logFreq.hashCode()
        result = 31 * result + magnitudes.contentHashCode()
        return result
    }
}
