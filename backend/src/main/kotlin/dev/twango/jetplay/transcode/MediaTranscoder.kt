package dev.twango.jetplay.transcode

import com.intellij.openapi.diagnostic.Logger
import dev.twango.jetplay.JetPlayConstants
import org.bytedeco.ffmpeg.global.avformat
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.io.File
import java.io.Reader
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Converts through the bundled FFmpeg executable, which owns stream timestamps and resampling. */
object MediaTranscoder {
    private val log = Logger.getInstance(MediaTranscoder::class.java)

    private const val DEFAULT_VIDEO_BITRATE = 2_000_000
    private val VP9_THREADS = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
    private const val OPUS_BITRATE = 128_000
    private const val OPUS_SAMPLE_RATE = 48_000
    private const val PROGRESS_COMPLETE = 100.0
    private const val PROGRESS_MAX = 99.9
    private const val PROGRESS_PRECISION = 10
    private const val PROCESS_POLL_MILLIS = 100L
    private const val MAX_OUTPUT_CHARS = 4096
    private const val MAX_PROGRESS_LINE_CHARS = 256

    private data class RawAudioHint(val format: String, val sampleRate: Int, val channels: Int)
    private data class SourceInfo(val durationMicros: Long, val videoBitrate: Int, val videoEndSeconds: Double?)

    private val RAW_AUDIO_HINTS = mapOf(
        "pcmu" to RawAudioHint("mulaw", 8000, 1),
        "ulaw" to RawAudioHint("mulaw", 8000, 1),
        "pcma" to RawAudioHint("alaw", 8000, 1),
        "alaw" to RawAudioHint("alaw", 8000, 1),
        "g722" to RawAudioHint("g722", 16000, 1),
        "slin" to RawAudioHint("s16le", 8000, 1),
    )

    internal val rawAudioExtensions: Set<String> get() = RAW_AUDIO_HINTS.keys

    /** Convenience for standalone callers; successful output ownership transfers to the caller. */
    fun transcode(inputFile: File, onProgress: (Double) -> Unit = {}): File {
        val output = Files.createTempFile("jetplay-", ".webm").toFile()
        try {
            transcode(inputFile, output, onProgress = onProgress)
            return output
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        }
    }

    /** The caller owns [outputFile] before crossing any cancellable coroutine boundary. */
    fun transcode(
        inputFile: File,
        outputFile: File,
        checkCancelled: () -> Unit = {},
        onProgress: (Double) -> Unit = {},
    ) {
        require(inputFile.canonicalFile != outputFile.canonicalFile) { "Transcode output must differ from the source" }
        var complete = false
        try {
            checkCancelled()
            val hint = RAW_AUDIO_HINTS[inputFile.extension.lowercase()]
            val source = probeSource(inputFile, hint, checkCancelled)
            checkCancelled()
            val executable = FfmpegAvailability.executable
            val command = buildCommand(executable, inputFile, outputFile, source, hint)
            val output = ProcessOutput(source.durationMicros, checkCancelled, onProgress)
            if (source.durationMicros <= 0L) onProgress(-1.0)
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            try {
                process.inputStream.bufferedReader().use { reader ->
                    while (true) {
                        checkCancelled()
                        output.read(reader, untilEof = false)
                        if (process.waitFor(PROCESS_POLL_MILLIS, TimeUnit.MILLISECONDS)) break
                    }
                    output.read(reader, untilEof = true)
                }
                checkCancelled()
                check(process.exitValue() == 0) { "FFmpeg conversion failed: ${output.diagnostics()}" }
            } finally {
                if (process.isAlive) process.destroyForcibly()
                // join is uninterruptible, so cancellation cannot leave a child process unreaped.
                process.onExit().join()
            }
            checkCancelled()
            onProgress(PROGRESS_COMPLETE)
            complete = true
            log.info("Transcoded ${inputFile.name} -> ${outputFile.name} (${outputFile.length() / JetPlayConstants.BYTES_PER_KB}KB)")
        } finally {
            if (!complete) outputFile.delete()
        }
    }

    private fun probeSource(input: File, hint: RawAudioHint?, checkCancelled: () -> Unit): SourceInfo {
        val grabber = FFmpegFrameGrabber(input).apply {
            sampleMode = FrameGrabber.SampleMode.RAW
            imageMode = FrameGrabber.ImageMode.RAW
            hint?.let {
                format = it.format
                sampleRate = it.sampleRate
                audioChannels = it.channels
            }
        }
        try {
            checkCancelled()
            grabber.start()
            checkCancelled()
            return SourceInfo(
                grabber.lengthInTime,
                grabber.videoBitrate.takeIf { it > 0 } ?: DEFAULT_VIDEO_BITRATE,
                videoEndSeconds(grabber),
            )
        } finally {
            log.safely("grabber.stop") { grabber.stop() }
            log.safely("grabber.release") { grabber.release() }
        }
    }

    private fun videoEndSeconds(grabber: FFmpegFrameGrabber): Double? {
        val context = grabber.formatContext ?: return null
        val index = (0 until context.nb_streams()).firstOrNull {
            val stream = context.streams(it)
            stream.codecpar().codec_type() == avutil.AVMEDIA_TYPE_VIDEO &&
                stream.disposition() and avformat.AV_DISPOSITION_ATTACHED_PIC == 0
        } ?: return null
        val stream = context.streams(index)
        val timeBase = stream.time_base()
        return if (stream.duration() > 0 && timeBase.num() > 0 && timeBase.den() > 0) {
            val scale = timeBase.num().toDouble() / timeBase.den()
            val start = if (stream.start_time() == avutil.AV_NOPTS_VALUE) 0.0 else stream.start_time() * scale
            val origin = if (context.start_time() == avutil.AV_NOPTS_VALUE) 0.0 else context.start_time() / 1_000_000.0
            (start - origin + stream.duration() * scale).takeIf { it.isFinite() && it > 0 }
        } else {
            null
        }
    }

    private fun buildCommand(
        executable: String,
        input: File,
        output: File,
        source: SourceInfo,
        hint: RawAudioHint?,
    ): List<String> = buildList {
        addAll(listOf(executable, "-hide_banner", "-loglevel", "error", "-nostats", "-nostdin", "-y"))
        hint?.let { addAll(listOf("-f", it.format, "-ar", it.sampleRate.toString(), "-ac", it.channels.toString())) }
        addAll(listOf("-i", input.absolutePath, "-map", "0:V:0?", "-map", "0:a:0?", "-sn", "-dn"))
        addAll(listOf("-c:v", "libvpx-vp9", "-b:v", source.videoBitrate.toString(), "-pix_fmt", "yuv420p"))
        addAll(listOf("-deadline", "realtime", "-cpu-used", "8", "-row-mt", "1", "-threads", VP9_THREADS.toString()))
        addAll(listOf("-fps_mode:v", "passthrough", "-enc_time_base:v", "demux"))
        source.videoEndSeconds?.let { end ->
            // Decoder frame durations can span a previous VFR gap; bound the final packet to the source stream's end.
            addAll(listOf("-bsf:v", "setts=duration=max(0\\,min(DURATION\\,$end/TB-PTS))"))
        }
        addAll(listOf("-c:a", "libopus", "-b:a", OPUS_BITRATE.toString(), "-ar", OPUS_SAMPLE_RATE.toString()))
        addAll(listOf("-progress", "pipe:1", "-stats_period", "0.2", "-f", "webm", output.absolutePath))
    }

    /** Consume machine progress and retain only a bounded diagnostic tail for conversion failures. */
    private class ProcessOutput(
        private val durationMicros: Long,
        private val checkCancelled: () -> Unit,
        private val onProgress: (Double) -> Unit,
    ) {
        private val line = StringBuilder()
        private val tail = StringBuilder()
        private val buffer = CharArray(MAX_OUTPUT_CHARS)
        private var lastTenth = -1L

        fun read(reader: Reader, untilEof: Boolean) {
            while (untilEof || reader.ready()) {
                checkCancelled()
                val count = reader.read(buffer)
                if (count < 0) break
                tail.append(buffer, 0, count)
                if (tail.length > MAX_OUTPUT_CHARS) tail.delete(0, tail.length - MAX_OUTPUT_CHARS)
                for (i in 0 until count) {
                    val character = buffer[i]
                    if (character == '\n') {
                        report(line.toString())
                        line.setLength(0)
                    } else if (line.length < MAX_PROGRESS_LINE_CHARS) {
                        line.append(character)
                    }
                }
            }
        }

        fun diagnostics(): String = tail.toString().trim()

        private fun report(value: String) {
            if (durationMicros <= 0 || !value.startsWith("out_time_us=")) return
            val timestamp = value.substringAfter('=').trim().toLongOrNull() ?: return
            val percent = (timestamp.toDouble() * PROGRESS_COMPLETE / durationMicros).coerceIn(0.0, PROGRESS_MAX)
            val tenth = (percent * PROGRESS_PRECISION).toLong()
            if (tenth != lastTenth) {
                lastTenth = tenth
                onProgress(percent)
            }
        }
    }
}
