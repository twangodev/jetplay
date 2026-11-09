package dev.twango.jetplay.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MediaResourcesTest {
    @Test
    fun disposalReleasesUrlsAndOnlyDeletesOwnedFiles() {
        val original = Files.createTempFile("jetplay-original", ".mp3").toFile()
        val temporary = Files.createTempFile("jetplay-converted", ".webm").toFile()
        val released = mutableListOf<String>()
        var nextToken = 0
        val resources = MediaResources(serve = { "url-${++nextToken}" }, release = { released.add(it) })
        try {
            resources.register(original)
            resources.register(temporary, temporary = true)
            resources.dispose()
            resources.dispose()
            assertEquals(listOf("url-1", "url-2"), released)
            assertTrue(original.exists())
            assertFalse(temporary.exists())
        } finally {
            resources.dispose()
            original.delete()
            temporary.delete()
        }
    }

    @Test
    fun temporaryFileRegisteredAfterDisposalIsDeletedWithoutServing() {
        val file = Files.createTempFile("jetplay-late", ".webm").toFile()
        val resources = MediaResources(serve = { error("Disposed owner must not serve media") })
        try {
            resources.dispose()
            assertNull(resources.register(file, temporary = true))
            assertFalse(file.exists())
        } finally {
            file.delete()
        }
    }

    @Test
    fun disposalCannotMissTemporaryFileWhoseUrlIsBeingRegistered() {
        val file = Files.createTempFile("jetplay-racing", ".webm").toFile()
        val serveStarted = CountDownLatch(1)
        val finishServe = CountDownLatch(1)
        val disposeStarted = CountDownLatch(1)
        val released = Collections.synchronizedList(mutableListOf<String>())
        val executor = Executors.newFixedThreadPool(2)
        val resources = MediaResources(
            serve = {
                serveStarted.countDown()
                check(finishServe.await(10, TimeUnit.SECONDS))
                "racing-url"
            },
            release = { released.add(it) },
        )
        try {
            val registration = executor.submit<String?> { resources.register(file, temporary = true) }
            assertTrue(serveStarted.await(10, TimeUnit.SECONDS))
            val disposal = executor.submit {
                disposeStarted.countDown()
                resources.dispose()
            }
            assertTrue(disposeStarted.await(10, TimeUnit.SECONDS))
            assertFalse(disposal.isDone)
            finishServe.countDown()
            assertEquals("racing-url", registration.get(10, TimeUnit.SECONDS))
            disposal.get(10, TimeUnit.SECONDS)
            assertEquals(listOf("racing-url"), released)
            assertFalse(file.exists())
        } finally {
            finishServe.countDown()
            executor.shutdownNow()
            resources.dispose()
            file.delete()
        }
    }
}
