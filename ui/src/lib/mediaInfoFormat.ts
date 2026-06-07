// Shared codec-inspector formatters so the audio and video players render identical values.

export function formatSampleRate(hz: number): string {
  // Precision that keeps the conventional rates (44.1, 22.05, 11.025) yet trims trailing zeros.
  const conventionalRateDecimals = 3
  const kHz = Number((hz / 1000).toFixed(conventionalRateDecimals))
  return `${kHz} kHz`
}

export function formatBitrate(bps: number): string {
  // kbps stays readable even for lossless streams running several thousand kbps, so never Mbps.
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
