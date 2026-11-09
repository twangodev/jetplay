import parseDuration from 'parse-duration'

/** Library-supported durations, expressed in nonnegative seconds suitable for seeking. */
export function parseTimestamp(input: string): number | null {
  const seconds = parseDuration(input.trim(), 's')
  return seconds !== null && seconds >= 0 && Number.isSafeInteger(Math.round(seconds * 1000)) ? seconds : null
}

/** Stable millisecond precision for jump inputs and copied file references. */
export function formatTimestamp(seconds: number): string {
  const milliseconds = Math.round(seconds * 1000)
  if (!Number.isFinite(seconds) || seconds < 0 || !Number.isSafeInteger(milliseconds)) return '00:00.000'

  const hours = Math.floor(milliseconds / 3_600_000)
  const minutes = Math.floor(milliseconds / 60_000) % 60
  const wholeSeconds = Math.floor(milliseconds / 1000) % 60
  const fraction = String(milliseconds % 1000).padStart(3, '0')
  const tail = `${String(minutes).padStart(2, '0')}:${String(wholeSeconds).padStart(2, '0')}.${fraction}`
  return hours > 0 ? `${hours}:${tail}` : tail
}
