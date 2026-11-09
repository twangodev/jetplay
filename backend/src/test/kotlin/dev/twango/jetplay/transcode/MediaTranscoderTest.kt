package dev.twango.jetplay.transcode

import dev.twango.jetplay.media.MediaClassification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.abs

class MediaTranscoderTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun demuxerHintsCoverEveryRawAudioExtension() {
        assertEquals(MediaClassification.rawAudioExtensions, MediaTranscoder.rawAudioExtensions)
    }

    @Test
    fun preservesVariableFrameTimestampsAndSourceEnd() {
        Assume.assumeTrue("FFmpeg native libraries required", FfmpegAvailability.available)
        val output = temporary.newFile("variable.webm")
        MediaTranscoder.transcode(fixture("vfr.mp4"), output)
        val timestamps = mutableListOf<Long>()
        FFmpegFrameGrabber(output).use { grabber ->
            grabber.imageMode = FrameGrabber.ImageMode.RAW
            grabber.start()
            while (grabber.grabImage() != null) timestamps.add(grabber.timestamp)
        }
        assertEquals(3, timestamps.size)
        for ((expected, actual) in listOf(0L, 33_333L, 1_966_667L).zip(timestamps)) {
            assertTrue("expected frame at $expected us, got $actual us", abs(expected - actual) <= 1000L)
        }
        val info = MediaInfoExtractor.extract(output)
        assertNotNull(info)
        assertTrue("VFR output must retain the two-second endpoint", info!!.durationMs!! in 1999L..2001L)
    }

    @Test
    fun convertsAudioThroughTheBundledExecutable() {
        Assume.assumeTrue("FFmpeg native libraries required", FfmpegAvailability.available)
        val output = temporary.newFile("audio.webm")
        val progress = mutableListOf<Double>()
        MediaTranscoder.transcode(fixture("24bit.flac"), output, onProgress = { progress.add(it) })
        val info = MediaInfoExtractor.extract(output)
        assertNotNull(info)
        assertEquals("opus", info!!.codec)
        assertEquals(48_000, info.sampleRateHz)
        assertEquals(100.0, progress.last(), 0.0)
    }

    @Test
    fun failureDeletesThePartiallyWrittenOutput() {
        Assume.assumeTrue("FFmpeg native libraries required", FfmpegAvailability.available)
        val output = temporary.newFile("failed.webm")
        val failure = IllegalStateException("consumer stopped")
        val thrown = assertThrows(IllegalStateException::class.java) {
            MediaTranscoder.transcode(fixture("vfr.mp4"), output, onProgress = { throw failure })
        }
        assertTrue(thrown === failure)
        assertFalse("failed outputs must not remain until IDE exit", output.exists())
    }

    @Test
    fun coroutineCancellationStopsConversionAndDeletesOutput() {
        Assume.assumeTrue("FFmpeg native libraries required", FfmpegAvailability.available)
        val output = temporary.newFile("cancelled.webm")
        val job = Job()
        try {
            assertThrows(CancellationException::class.java) {
                MediaTranscoder.transcode(
                    fixture("vfr.mp4"),
                    output,
                    checkCancelled = { job.ensureActive() },
                    onProgress = { job.cancel() },
                )
            }
            assertFalse(output.exists())
        } finally {
            job.cancel()
        }
    }

    private fun fixture(name: String): File = File(checkNotNull(javaClass.getResource("/media/$name")).toURI())
}
