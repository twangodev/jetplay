export interface PlaybackPreferences {
  volume: number
  muted: boolean
  playbackRate: number
}

export interface PlaybackMemory {
  preferences: PlaybackPreferences
  positionSeconds: number
}

export interface PlaybackMemoryUpdate {
  preferences?: Partial<PlaybackPreferences>
  positionSeconds?: number
}

const PREFERENCES_KEY = 'jetplay.playback.preferences.v1'
const POSITIONS_KEY = 'jetplay.playback.positions.v1'
const MAX_POSITIONS = 100
const POSITION_WRITE_INTERVAL_MS = 1000

function preferencesFrom(value: unknown): PlaybackPreferences {
  const candidate = value && typeof value === 'object' ? value as Partial<PlaybackPreferences> : {}
  return {
    volume: typeof candidate.volume === 'number' && Number.isFinite(candidate.volume) && candidate.volume >= 0 && candidate.volume <= 1
      ? candidate.volume : 1,
    muted: typeof candidate.muted === 'boolean' ? candidate.muted : false,
    playbackRate: typeof candidate.playbackRate === 'number' && Number.isFinite(candidate.playbackRate) && candidate.playbackRate >= 0.25 && candidate.playbackRate <= 2
      ? candidate.playbackRate : 1,
  }
}

function validPosition(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0
}

function readStored(key: string): unknown {
  try {
    const value = window.localStorage.getItem(key)
    return value === null ? null : JSON.parse(value)
  } catch {
    return null
  }
}

function storedPositions(): Map<string, number> {
  const value = readStored(POSITIONS_KEY)
  if (!value || typeof value !== 'object' || Array.isArray(value)) return new Map()
  return new Map(Object.entries(value).filter((entry): entry is [string, number] => validPosition(entry[1])))
}

/** Attach after assigning src. The returned cleanup saves progress and removes every listener. */
export function attachPlaybackMemory(
  media: HTMLMediaElement,
  identity: string,
  onPreferences?: (preferences: PlaybackPreferences) => void,
): () => void {
  const nativeSave = window.jetplaySavePlayback
  const initial = window.jetplay?.playbackMemory
  let preferences = preferencesFrom(initial?.preferences ?? (nativeSave ? null : readStored(PREFERENCES_KEY)))
  const savedPosition = initial?.positionSeconds ?? (nativeSave ? 0 : storedPositions().get(identity))
  const positionToRestore = validPosition(savedPosition) ? savedPosition : 0
  const source = media.getAttribute('src')
  let disposed = false
  let preferencesReady = false
  let positionReady = false
  let lastPosition = positionToRestore
  let lastPersistedPosition: number | undefined
  let lastPositionWriteAt = 0
  let completed = false

  const isCurrentSource = () => !disposed && media.getAttribute('src') === source

  function persist(update: PlaybackMemoryUpdate) {
    if (disposed) return
    try {
      if (nativeSave) {
        void nativeSave(update).catch(() => {})
      } else {
        if (update.preferences) {
          const latest = preferencesFrom(readStored(PREFERENCES_KEY) ?? initial?.preferences)
          window.localStorage.setItem(PREFERENCES_KEY, JSON.stringify({ ...latest, ...update.preferences }))
        }
        if (update.positionSeconds !== undefined) {
          const positions = storedPositions()
          positions.delete(identity)
          positions.set(identity, update.positionSeconds)
          const bounded = Array.from(positions).slice(-MAX_POSITIONS)
          window.localStorage.setItem(POSITIONS_KEY, JSON.stringify(Object.fromEntries(bounded)))
        }
      }
    } catch {
      // Unavailable storage or an IDE closing its bridge must never interrupt playback.
    }
  }

  function applyPreferences() {
    preferencesReady = false
    media.volume = preferences.volume
    media.muted = preferences.muted
    media.defaultPlaybackRate = preferences.playbackRate
    media.playbackRate = preferences.playbackRate
    onPreferences?.({ ...preferences })
    preferencesReady = true
  }

  function capturePosition() {
    if (!isCurrentSource() || !positionReady || !validPosition(media.currentTime)) return
    if (completed && media.currentTime < media.duration) completed = false
    lastPosition = completed || (media.ended && !media.loop) ? 0 : media.currentTime
  }

  function savePosition(force: boolean) {
    if (!isCurrentSource() || !positionReady) return
    capturePosition()
    const now = Date.now()
    if (!force && now - lastPositionWriteAt < POSITION_WRITE_INTERVAL_MS) return
    if (lastPersistedPosition === lastPosition) return
    persist({ positionSeconds: lastPosition })
    lastPersistedPosition = lastPosition
    lastPositionWriteAt = now
  }

  function restorePosition() {
    if (!isCurrentSource() || positionReady || media.readyState < 1 || !Number.isFinite(media.duration) || media.duration <= 0) return
    const target = positionToRestore < media.duration ? positionToRestore : 0
    if (target > 0 && media.seekable.length > 0) {
      let seekable = false
      for (let i = 0; i < media.seekable.length; i++) {
        if (target >= media.seekable.start(i) && target <= media.seekable.end(i)) seekable = true
      }
      if (!seekable) return
    }
    try {
      if (target !== media.currentTime) media.currentTime = target
      lastPosition = target
      positionReady = true
      if (target !== positionToRestore || (savedPosition !== undefined && !validPosition(savedPosition))) savePosition(true)
    } catch {
      // Some media engines reject seeks until data arrives; retry on progress/canplay.
    }
  }

  function onMetadata() {
    if (!isCurrentSource()) return
    // load() can reset playbackRate after attachment, so restore it once metadata arrives.
    applyPreferences()
    restorePosition()
  }

  function onPreferenceChange() {
    if (!isCurrentSource() || !preferencesReady) return
    const next = preferencesFrom({ volume: media.volume, muted: media.muted, playbackRate: media.playbackRate })
    const changes: Partial<PlaybackPreferences> = {}
    if (next.volume !== preferences.volume) changes.volume = next.volume
    if (next.muted !== preferences.muted) changes.muted = next.muted
    if (next.playbackRate !== preferences.playbackRate) changes.playbackRate = next.playbackRate
    if (Object.keys(changes).length === 0) return
    preferences = next
    onPreferences?.({ ...preferences })
    persist({ preferences: changes })
  }

  const onTimeUpdate = () => savePosition(false)
  const onPositionChange = () => savePosition(true)
  const onEnded = () => {
    if (!isCurrentSource() || !positionReady) return
    completed = !media.loop
    lastPosition = media.loop ? media.currentTime : 0
    if (lastPersistedPosition !== lastPosition) {
      persist({ positionSeconds: lastPosition })
      lastPersistedPosition = lastPosition
      lastPositionWriteAt = Date.now()
    }
  }

  media.addEventListener('loadedmetadata', onMetadata)
  for (const event of ['durationchange', 'progress', 'canplay']) media.addEventListener(event, restorePosition)
  for (const event of ['volumechange', 'ratechange']) media.addEventListener(event, onPreferenceChange)
  media.addEventListener('timeupdate', onTimeUpdate)
  for (const event of ['seeked', 'pause']) media.addEventListener(event, onPositionChange)
  media.addEventListener('ended', onEnded)
  window.addEventListener('pagehide', onPositionChange)

  applyPreferences()
  if (media.readyState >= 1) onMetadata()

  return () => {
    if (disposed) return
    capturePosition()
    if (positionReady && lastPersistedPosition !== lastPosition) persist({ positionSeconds: lastPosition })
    disposed = true
    media.removeEventListener('loadedmetadata', onMetadata)
    for (const event of ['durationchange', 'progress', 'canplay']) media.removeEventListener(event, restorePosition)
    for (const event of ['volumechange', 'ratechange']) media.removeEventListener(event, onPreferenceChange)
    media.removeEventListener('timeupdate', onTimeUpdate)
    for (const event of ['seeked', 'pause']) media.removeEventListener(event, onPositionChange)
    media.removeEventListener('ended', onEnded)
    window.removeEventListener('pagehide', onPositionChange)
  }
}
