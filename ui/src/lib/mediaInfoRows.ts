import { formatTime } from './formatTime'
import { formatBitrate, formatBytes, formatFrameRate, formatSampleRate } from './mediaInfoFormat'

export type InfoRow = { label: string; value: string }

export function videoInfoRows(m?: MediaInfo): InfoRow[] {
  if (!m) return []
  const rows: InfoRow[] = []
  if (m.width && m.height) rows.push({ label: 'Resolution', value: `${m.width}×${m.height}` })
  if (m.frameRate) rows.push({ label: 'Frame rate', value: formatFrameRate(m.frameRate) })
  if (m.videoCodec) rows.push({ label: 'Video codec', value: m.videoCodec })
  if (m.pixelFormat) rows.push({ label: 'Color', value: m.pixelFormat })
  if (m.videoBitrateBps) rows.push({ label: 'Video bitrate', value: formatBitrate(m.videoBitrateBps) })
  return rows
}

export function audioInfoRows(m?: MediaInfo): InfoRow[] {
  if (!m) return []
  const rows: InfoRow[] = []
  if (m.codec) rows.push({ label: 'Audio codec', value: m.codec })
  if (m.sampleRateHz) rows.push({ label: 'Sample rate', value: formatSampleRate(m.sampleRateHz) })
  if (m.channels)
    rows.push({ label: 'Channels', value: m.channelLabel ? `${m.channels} (${m.channelLabel})` : String(m.channels) })
  if (m.bitDepth) rows.push({ label: 'Bit depth', value: m.bitDepth })
  if (m.bitrateBps) rows.push({ label: 'Audio bitrate', value: formatBitrate(m.bitrateBps) })
  return rows
}

export function generalInfoRows(m?: MediaInfo): InfoRow[] {
  if (!m) return []
  const rows: InfoRow[] = []
  if (m.container) rows.push({ label: 'Container', value: m.container.toUpperCase() })
  if (m.durationMs) rows.push({ label: 'Duration', value: formatTime(m.durationMs / 1000) })
  if (m.sizeBytes) rows.push({ label: 'Size', value: formatBytes(m.sizeBytes) })
  return rows
}
