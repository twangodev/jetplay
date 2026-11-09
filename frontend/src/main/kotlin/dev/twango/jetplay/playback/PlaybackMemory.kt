package dev.twango.jetplay.playback

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

private const val MIN_PLAYBACK_RATE = 0.25
private const val MAX_PLAYBACK_RATE = 2.0

@Serializable
data class PlaybackPreferences(
    @Required val volume: Double = 1.0,
    @Required val muted: Boolean = false,
    @Required val playbackRate: Double = 1.0,
) {
    internal fun isValid(): Boolean = volume.isFinite() && volume in 0.0..1.0 &&
        playbackRate.isFinite() && playbackRate in MIN_PLAYBACK_RATE..MAX_PLAYBACK_RATE
}

@Serializable
data class PlaybackMemorySnapshot(
    val preferences: PlaybackPreferences = PlaybackPreferences(),
    val positionSeconds: Double = 0.0,
)

@Serializable
data class PlaybackPreferencePatch(
    val volume: Double? = null,
    val muted: Boolean? = null,
    val playbackRate: Double? = null,
) {
    fun applyTo(current: PlaybackPreferences): PlaybackPreferences = PlaybackPreferences(
        volume = volume ?: current.volume,
        muted = muted ?: current.muted,
        playbackRate = playbackRate ?: current.playbackRate,
    )
}

@Serializable
data class PlaybackMemoryUpdate(val preferences: PlaybackPreferencePatch? = null, val positionSeconds: Double? = null) {
    internal fun validate() {
        require(preferences?.applyTo(PlaybackPreferences())?.isValid() != false) { "Invalid playback preferences" }
        require(positionSeconds == null || (positionSeconds.isFinite() && positionSeconds >= 0.0)) {
            "Invalid playback position"
        }
    }
}

internal object PlaybackMemoryPayloads {
    private val json = Json { encodeDefaults = true }

    fun snapshotJson(snapshot: PlaybackMemorySnapshot): String = json.encodeToString(snapshot)

    fun parseUpdate(payload: String): PlaybackMemoryUpdate {
        val value = json.parseToJsonElement(payload).jsonObject
        // The serializer accepts quoted numbers; this bridge contract accepts actual JSON primitives only.
        requireNumber(value["positionSeconds"])
        value["preferences"]?.takeUnless { it == JsonNull }?.jsonObject?.let { preferences ->
            requireNumber(preferences["volume"])
            requireNumber(preferences["playbackRate"])
            preferences["muted"]?.let {
                require(it is JsonPrimitive && !it.isString && it.booleanOrNull != null) { "Invalid mute setting" }
            }
        }
        return json.decodeFromJsonElement<PlaybackMemoryUpdate>(value).also { it.validate() }
    }

    private fun requireNumber(value: JsonElement?) {
        val isNumber = value is JsonPrimitive && !value.isString && value.doubleOrNull != null
        require(value == null || value == JsonNull || isNumber) {
            "Expected a numeric playback setting"
        }
    }
}

/** Length prefixes keep project/file identities unambiguous without XML-invalid separators. */
internal fun playbackMemoryKey(projectIdentity: String, sourceUrl: String): String =
    "${projectIdentity.length}:$projectIdentity${sourceUrl.length}:$sourceUrl"
