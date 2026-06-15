package dev.twango.jetplay.rpc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TranscodeEventTest {

    // Chunk carries a ByteArray, so it hand-rolls equals/hashCode over contents; verify that contract holds,
    // since the frontend de-dups and the transport relies on value semantics rather than array identity.
    @Test
    fun chunksWithEqualBytesAreEqual() {
        val a = TranscodeEvent.Chunk(byteArrayOf(1, 2, 3))
        val b = TranscodeEvent.Chunk(byteArrayOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun chunksWithDifferentBytesAreNotEqual() {
        assertNotEquals(
            TranscodeEvent.Chunk(byteArrayOf(1, 2, 3)),
            TranscodeEvent.Chunk(byteArrayOf(1, 2, 4)),
        )
    }

    @Test
    fun progressCarriesPercent() {
        assertEquals(42.0, (TranscodeEvent.Progress(42.0)).percent, 0.0)
    }

    @Test
    fun failedCarriesMessage() {
        assertEquals("boom", (TranscodeEvent.Failed("boom")).message)
    }

    @Test
    fun terminalSingletonsAreDistinct() {
        val done: TranscodeEvent = TranscodeEvent.Done
        val unavailable: TranscodeEvent = TranscodeEvent.Unavailable
        assertNotEquals(done, unavailable)
    }
}
