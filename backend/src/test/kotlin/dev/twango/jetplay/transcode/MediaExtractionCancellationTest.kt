package dev.twango.jetplay.transcode

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

class MediaExtractionCancellationTest {
    @Test
    fun waveformPropagatesCancellationAfterDecodeStarts() {
        checkCancellation { input, checkpoint -> WaveformExtractor.extract(input, checkCancelled = checkpoint) }
    }

    @Test
    fun spectrogramPropagatesCancellationAfterDecodeStarts() {
        checkCancellation { input, checkpoint -> SpectrogramExtractor.extract(input, checkpoint) }
    }

    private fun checkCancellation(extract: (File, () -> Unit) -> Any?) {
        Assume.assumeTrue("FFmpeg native libraries required", FfmpegAvailability.available)
        val input = File(checkNotNull(javaClass.getResource("/media/24bit.flac")).toURI())
        val cancellation = CancellationException("editor closed")
        var checks = 0
        val thrown = assertThrows(CancellationException::class.java) {
            extract(input) {
                checks++
                if (checks == 4) throw cancellation
            }
        }
        assertTrue(thrown === cancellation)
    }
}
