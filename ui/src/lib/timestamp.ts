/** Seconds, m:ss, or h:mm:ss, with up to three fractional digits. */
export function parseTimestamp(input: string): number | null {
  const text = input.trim()
  if (!/^\d+(?:\.\d{1,3})?$/.test(text) && !/^\d+:(?:[0-5]\d:)?[0-5]\d(?:\.\d{1,3})?$/.test(text)) {
    return null
  }

  const [whole, fraction = ''] = text.split('.')
  const parts = whole.split(':').map(Number)
  if (parts.some((part) => !Number.isSafeInteger(part))) return null

  const seconds = parts.reduce((total, part) => total * 60 + part, 0)
  const milliseconds = seconds * 1000 + Number(fraction.padEnd(3, '0'))
  return Number.isSafeInteger(milliseconds) ? milliseconds / 1000 : null
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
