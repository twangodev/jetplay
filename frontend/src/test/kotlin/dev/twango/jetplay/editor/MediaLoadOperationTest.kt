package dev.twango.jetplay.editor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MediaLoadOperationTest {
    @Test
    fun successfulRequestReturnsItsValue() = runBlocking {
        assertEquals(42, mediaLoadOperation(1.seconds, { error("Unexpected failure") }) { 42 })
    }

    @Test
    fun rpcFailureCompletesTheFailureCallback() = runBlocking {
        val failure = IllegalStateException("Host disconnected")
        var reported: Exception? = null
        val result = mediaLoadOperation<Int>(1.seconds, { reported = it }) { throw failure }
        assertNull(result)
        assertEquals(failure.javaClass, reported?.javaClass)
        assertEquals(failure.message, reported?.message)
    }

    @Test
    fun requestTimeoutCompletesTheFailureCallback() = runBlocking {
        var reported: Exception? = null
        val result = mediaLoadOperation<Int>(10.milliseconds, { reported = it }) { awaitCancellation() }
        assertNull(result)
        assertTrue(reported is TimeoutCancellationException)
    }

    @Test
    fun cancellationDoesNotBecomeAVisibleLoadFailure() = runBlocking {
        val cancellation = CancellationException("Editor closed")
        var reported = false
        val result = runCatching {
            mediaLoadOperation<Int>(1.seconds, { reported = true }) { throw cancellation }
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(cancellation.message, result.exceptionOrNull()?.message)
        assertFalse(reported)
    }

    @Test
    fun parentTimeoutDoesNotBecomeAVisibleLoadFailure() = runBlocking {
        var reported = false
        val result = runCatching {
            withTimeout(10.milliseconds) {
                mediaLoadOperation<Int>(1.seconds, { reported = true }) { awaitCancellation() }
            }
        }
        assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
        assertFalse(reported)
    }
}
