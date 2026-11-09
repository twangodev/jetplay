package dev.twango.jetplay.playback

import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PlaybackMemoryServiceTest {
    private fun PlaybackPreferences.asPatch() = PlaybackPreferencePatch(volume, muted, playbackRate)

    @Test
    fun differentTabsMergeOnlyThePreferenceFieldsTheyChanged() {
        val memory = PlaybackMemoryService()
        memory.update("tab-b", "1", PlaybackMemoryUpdate(preferences = PlaybackPreferencePatch(volume = 0.2)))
        memory.update("tab-a", "1", PlaybackMemoryUpdate(preferences = PlaybackPreferencePatch(muted = true)))
        memory.update("tab-b", "1", PlaybackMemoryUpdate(preferences = PlaybackPreferencePatch(playbackRate = 2.0)))
        assertEquals(PlaybackPreferences(0.2, true, 2.0), memory.snapshot("new-tab", "1").preferences)
        memory.update("tab-a", "1", PlaybackMemoryUpdate(preferences = PlaybackPreferencePatch(muted = false)))
        assertEquals(PlaybackPreferences(0.2, false, 2.0), memory.snapshot("new-tab", "1").preferences)
    }

    @Test
    fun defaultsToFullVolumeNormalSpeedAndBeginning() {
        assertEquals(PlaybackMemorySnapshot(), PlaybackMemoryService().snapshot("source", "1:2"))
    }

    @Test
    fun preferencesAreGlobalAndPositionsArePerOriginalFile() {
        val memory = PlaybackMemoryService()
        val preferences = PlaybackPreferences(0.35, true, 1.5)
        memory.update("one", "1:2", PlaybackMemoryUpdate(preferences.asPatch(), 12.45))
        assertEquals(PlaybackMemorySnapshot(preferences, 12.45), memory.snapshot("one", "1:2"))
        assertEquals(PlaybackMemorySnapshot(preferences, 0.0), memory.snapshot("two", "1:2"))
    }

    @Test
    fun positionOnlyUpdateCannotOverwritePreferencesFromAnotherTab() {
        val memory = PlaybackMemoryService()
        val latestPreferences = PlaybackPreferences(0.2, true, 0.75)
        memory.update("one", "1:2", PlaybackMemoryUpdate(positionSeconds = 10.0))
        memory.update("two", "1:2", PlaybackMemoryUpdate(preferences = latestPreferences.asPatch()))
        memory.update("one", "1:2", PlaybackMemoryUpdate(positionSeconds = 11.0))
        assertEquals(PlaybackMemorySnapshot(latestPreferences, 11.0), memory.snapshot("one", "1:2"))
    }

    @Test
    fun preferencesOnlyUpdateDoesNotChangePosition() {
        val memory = PlaybackMemoryService()
        memory.update("source", "1:2", PlaybackMemoryUpdate(positionSeconds = 12.45))
        memory.update("source", "1:2", PlaybackMemoryUpdate(preferences = PlaybackPreferencePatch(0.4, false, 2.0)))
        assertEquals(12.45, memory.snapshot("source", "1:2").positionSeconds, 0.0)
    }

    @Test
    fun completedFileClearsSavedPosition() {
        val memory = PlaybackMemoryService()
        memory.update("source", "1:2", PlaybackMemoryUpdate(positionSeconds = 12.45))
        memory.update("source", "1:2", PlaybackMemoryUpdate(positionSeconds = 0.0))
        assertEquals(0.0, memory.snapshot("source", "1:2").positionSeconds, 0.0)
        assertEquals(0, memory.state.positions.size)
    }

    @Test
    fun changedSourceLengthOrModificationTimeDiscardsOldPosition() {
        val memory = PlaybackMemoryService()
        memory.update("source", "100:200", PlaybackMemoryUpdate(positionSeconds = 12.45))
        assertEquals(0.0, memory.snapshot("source", "101:200").positionSeconds, 0.0)
        assertEquals(0, memory.state.positions.size)
        memory.update("source", "101:200", PlaybackMemoryUpdate(positionSeconds = 8.0))
        assertEquals(0.0, memory.snapshot("source", "101:201").positionSeconds, 0.0)
    }

    @Test
    fun xmlRoundTripRestoresPreferencesAndPositions() {
        val memory = PlaybackMemoryService()
        val preferences = PlaybackPreferences(0.45, true, 1.25)
        val key = playbackMemoryKey("remote://host/project", "remote://host/project/voice & music.wav")
        memory.update(key, "100:200", PlaybackMemoryUpdate(preferences.asPatch(), 83.45))
        val xml = XmlSerializer.serialize(memory.state)
        val reopened = PlaybackMemoryService()
        reopened.loadState(XmlSerializer.deserialize(xml, PlaybackMemoryState::class.java))
        assertEquals(PlaybackMemorySnapshot(preferences, 83.45), reopened.snapshot(key, "100:200"))
    }

    @Test
    fun stateSnapshotsAndLoadedStateDoNotLeakMutableReferences() {
        val memory = PlaybackMemoryService()
        memory.update("source", "1:2", PlaybackMemoryUpdate(positionSeconds = 3.0))
        val state = memory.state
        val reopened = PlaybackMemoryService()
        reopened.loadState(state)
        state.positions.first().positionSeconds = 999.0
        state.volume = 0.0
        assertEquals(PlaybackMemorySnapshot(positionSeconds = 3.0), memory.snapshot("source", "1:2"))
        assertEquals(PlaybackMemorySnapshot(positionSeconds = 3.0), reopened.snapshot("source", "1:2"))
    }

    @Test
    fun boundsHistoryAndRetainsRecentlyUpdatedFiles() {
        val memory = PlaybackMemoryService()
        repeat(PlaybackMemoryService.MAX_REMEMBERED_FILES) {
            memory.update("source-$it", "1:2", PlaybackMemoryUpdate(positionSeconds = 3.0))
        }
        memory.update("source-0", "1:2", PlaybackMemoryUpdate(positionSeconds = 4.0))
        memory.update("new", "1:2", PlaybackMemoryUpdate(positionSeconds = 5.0))
        assertEquals(PlaybackMemoryService.MAX_REMEMBERED_FILES, memory.state.positions.size)
        assertEquals(4.0, memory.snapshot("source-0", "1:2").positionSeconds, 0.0)
        assertEquals(0.0, memory.snapshot("source-1", "1:2").positionSeconds, 0.0)
    }

    @Test
    fun invalidUpdateIsRejectedAtomically() {
        val memory = PlaybackMemoryService()
        val before = memory.snapshot("source", "1:2")
        for (position in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                memory.update("source", "1:2", PlaybackMemoryUpdate(PlaybackPreferencePatch(0.2, true, 2.0), position))
            }
        }
        for (preferences in listOf(
            PlaybackPreferences(-0.1), PlaybackPreferences(1.1), PlaybackPreferences(Double.NaN),
            PlaybackPreferences(playbackRate = 0.0), PlaybackPreferences(playbackRate = 2.1),
            PlaybackPreferences(playbackRate = Double.POSITIVE_INFINITY),
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                memory.update("source", "1:2", PlaybackMemoryUpdate(preferences.asPatch(), 3.0))
            }
        }
        assertEquals(before, memory.snapshot("source", "1:2"))
    }

    @Test
    fun corruptedLoadedStateIsSanitizedAndBounded() {
        val state = PlaybackMemoryState().apply {
            volume = Double.NaN
            playbackRate = -1.0
            repeat(PlaybackMemoryService.MAX_REMEMBERED_FILES + 10) { index ->
                positions.add(SavedPlaybackPosition().also {
                    it.key = "source-$index"
                    it.revision = "1:2"
                    it.positionSeconds = index.toDouble()
                })
            }
            positions.add(SavedPlaybackPosition().also {
                it.key = "invalid"
                it.revision = "1:2"
                it.positionSeconds = Double.NaN
            })
        }
        val memory = PlaybackMemoryService()
        memory.loadState(state)
        assertEquals(PlaybackPreferences(), memory.snapshot("source", "1:2").preferences)
        assertEquals(PlaybackMemoryService.MAX_REMEMBERED_FILES, memory.state.positions.size)
        assertEquals(0.0, memory.snapshot("invalid", "1:2").positionSeconds, 0.0)
    }

    @Test
    fun parallelTabUpdatesKeepEveryFilePosition() {
        val memory = PlaybackMemoryService()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val writes = (1..100).map { index ->
                executor.submit { memory.update("source-$index", "1:2", PlaybackMemoryUpdate(positionSeconds = index.toDouble())) }
            }
            writes.forEach { it.get(10, TimeUnit.SECONDS) }
            for (index in 1..100) assertEquals(index.toDouble(), memory.snapshot("source-$index", "1:2").positionSeconds, 0.0)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun identityDistinguishesProjectsRemoteAuthoritiesAndVfsSchemes() {
        val original = playbackMemoryKey("project-one", "remote://host-one/voice.wav")
        val different = listOf(
            playbackMemoryKey("project-two", "remote://host-one/voice.wav"),
            playbackMemoryKey("project-one", "remote://host-two/voice.wav"),
            playbackMemoryKey("project-one", "file:///voice.wav"),
            playbackMemoryKey("project-one-remote://host", "one/voice.wav"),
        )
        for (key in different) org.junit.Assert.assertNotEquals(original, key)
    }
}
