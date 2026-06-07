// Shared formatters for the codec inspector, used by both the audio and video
// players so the two render identical-looking values.

export function formatSampleRate(hz: number): string {
  // Keep enough precision for the conventional rates (44.1, 22.05, 11.025) while
  // trimming trailing zeros (48000 → "48", 44100 → "44.1").
  const k = Number((hz / 1000).toFixed(3))
  return `${k} kHz`
}

export function formatBitrate(bps: number): string {
  // kbps is the conventional unit at every magnitude (lossless audio and video
  // can run several thousand kbps), so we don't switch to Mbps.
  return `${Math.round(bps / 1000)} kbps`
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const kb = bytes / 1024
  if (kb < 1024) return `${kb.toFixed(kb < 10 ? 1 : 0)} KB`
  const mb = kb / 1024
  if (mb < 1024) return `${mb.toFixed(mb < 10 ? 1 : 0)} MB`
  return `${(mb / 1024).toFixed(1)} GB`
}

export function formatFrameRate(fps: number): string {
  // 24 → "24 fps", 23.976023… → "23.976 fps", 29.97 → "29.97 fps".
  return `${Number(fps.toFixed(3))} fps`
}
