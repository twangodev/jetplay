package dev.twango.jetplay.playback

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/** Small, local-only history. Neither browser storage nor a backend RPC is needed in Remote Dev. */
@Service(Service.Level.APP)
@State(name = "JetplayPlaybackMemory", storages = [Storage(value = "jetplay-playback.xml", roamingType = RoamingType.DISABLED)])
class PlaybackMemoryService : PersistentStateComponent<PlaybackMemoryState> {
    private var memory = PlaybackMemoryState()

    @Synchronized
    override fun getState(): PlaybackMemoryState = copyState(memory)

    @Synchronized
    override fun loadState(state: PlaybackMemoryState) {
        val preferences = PlaybackPreferences(state.volume, state.muted, state.playbackRate)
            .takeIf { it.isValid() } ?: PlaybackPreferences()
        memory = PlaybackMemoryState().apply {
            volume = preferences.volume
            muted = preferences.muted
            playbackRate = preferences.playbackRate
            positions = state.positions
                .filter {
                    it.key.isNotBlank() && it.revision.isNotBlank() &&
                        it.positionSeconds.isFinite() && it.positionSeconds > 0.0
                }
                .asReversed()
                .distinctBy { it.key }
                .take(MAX_REMEMBERED_FILES)
                .asReversed()
                .map { copyPosition(it) }
                .toMutableList()
        }
    }

    @Synchronized
    fun snapshot(key: String, revision: String): PlaybackMemorySnapshot {
        // A changed source must not inherit the old file's playback position.
        memory.positions.removeAll { it.key == key && it.revision != revision }
        return PlaybackMemorySnapshot(
            preferences = PlaybackPreferences(memory.volume, memory.muted, memory.playbackRate),
            positionSeconds = memory.positions.lastOrNull { it.key == key }?.positionSeconds ?: 0.0,
        )
    }

    @Synchronized
    fun update(key: String, revision: String, update: PlaybackMemoryUpdate) {
        update.validate()
        require(key.isNotBlank() && revision.isNotBlank()) { "Missing playback file identity" }
        update.preferences?.let {
            val merged = it.applyTo(PlaybackPreferences(memory.volume, memory.muted, memory.playbackRate))
            memory.volume = merged.volume
            memory.muted = merged.muted
            memory.playbackRate = merged.playbackRate
        }
        update.positionSeconds?.let { position ->
            memory.positions.removeAll { it.key == key }
            if (position > 0.0) {
                memory.positions.add(
                    SavedPlaybackPosition().also {
                        it.key = key
                        it.revision = revision
                        it.positionSeconds = position
                    },
                )
            }
            while (memory.positions.size > MAX_REMEMBERED_FILES) memory.positions.removeAt(0)
        }
    }

    companion object {
        internal const val MAX_REMEMBERED_FILES = 100

        private fun copyPosition(position: SavedPlaybackPosition): SavedPlaybackPosition =
            SavedPlaybackPosition().also {
                it.key = position.key
                it.revision = position.revision
                it.positionSeconds = position.positionSeconds
            }

        private fun copyState(state: PlaybackMemoryState): PlaybackMemoryState = PlaybackMemoryState().also {
            it.volume = state.volume
            it.muted = state.muted
            it.playbackRate = state.playbackRate
            it.positions = state.positions.map { position -> copyPosition(position) }.toMutableList()
        }
    }
}

// Mutable XML beans stay private to serialization; callers receive immutable snapshots and submit partial updates.
class PlaybackMemoryState {
    var volume: Double = 1.0
    var muted: Boolean = false
    var playbackRate: Double = 1.0
    var positions: MutableList<SavedPlaybackPosition> = mutableListOf()
}

class SavedPlaybackPosition {
    var key: String = ""
    var revision: String = ""
    var positionSeconds: Double = 0.0
}
