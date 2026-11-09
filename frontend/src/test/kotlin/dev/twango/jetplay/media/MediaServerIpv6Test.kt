package dev.twango.jetplay.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.system.exitProcess

class MediaServerIpv6Test {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun advertisedUrlWorksWhenJvmPrefersIpv6() {
        val output = temporary.newFile("ipv6 probe.log")
        val launcher = temporary.newFile("ipv6 probe.jar")
        // The SDK classpath exceeds Linux's per-argument limit; the JDK handles manifest URLs and line wrapping.
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes[Attributes.Name.MAIN_CLASS] = MediaServerIpv6Probe::class.java.name
            mainAttributes[Attributes.Name.CLASS_PATH] = runtimeClasspath()
        }
        JarOutputStream(launcher.outputStream(), manifest).use { }
        val java = File(System.getProperty("java.home"), "bin/java").path
        val process = ProcessBuilder(
            java,
            "--add-exports=java.base/sun.nio.fs=ALL-UNNAMED",
            "-Djava.net.preferIPv6Addresses=true",
            "-jar",
            launcher.path,
        ).redirectErrorStream(true).redirectOutput(output).start()
        try {
            assertTrue("IPv6 probe timed out", process.waitFor(15, TimeUnit.SECONDS))
            assertEquals(output.readText(), 0, process.exitValue())
        } finally {
            process.destroyForcibly()
        }
    }

    private fun runtimeClasspath(): String = buildSet {
        addAll(System.getProperty("java.class.path").split(File.pathSeparator))
        generateSequence(javaClass.classLoader) { it.parent }.filterIsInstance<URLClassLoader>().forEach { loader ->
            loader.getURLs().filter { it.protocol == "file" }.forEach { add(File(it.toURI()).path) }
        }
    }.joinToString(" ") { File(it).toURI().toASCIIString() }
}

/** An isolated JVM is necessary because Java caches its IPv6 preference before any test can change it. */
internal object MediaServerIpv6Probe {
    @JvmStatic
    fun main(args: Array<String>) {
        val file = Files.createTempFile("jetplay-ipv6-media", ".mp3").toFile().apply { writeText("media") }
        var url: String? = null
        val status = try {
            url = MediaServer.serve(file)
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            try {
                check(connection.responseCode == 200)
                check(connection.inputStream.readBytes().decodeToString() == "media")
            } finally {
                connection.disconnect()
            }
            0
        } catch (failure: Throwable) {
            failure.printStackTrace()
            1
        } finally {
            url?.let(MediaServer::release)
            file.delete()
        }
        exitProcess(status)
    }
}
