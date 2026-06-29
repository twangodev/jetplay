export {}

declare global {
  // Codec inspector metadata; fields are optional because FFmpeg may not determine every value.
  interface MediaInfo {
    codec?: string
    container?: string
    sampleRateHz?: number
    channels?: number
    channelLabel?: string
    bitDepth?: string
    bitrateBps?: number
    durationMs?: number
    sizeBytes?: number
    width?: number
    height?: number
    frameRate?: number
    videoCodec?: string
    pixelFormat?: string
    videoBitrateBps?: number
    tags?: { label: string; value: string }[]
    albumArt?: string
  }

  // The bridge sends exactly one of two shapes: an unavailable marker, or a fully-populated result.
  // `ok: false` means the IDE has nothing to show (ffmpeg absent or unsupported).
  type SpectrogramData = { ok: false } | SpectrogramReady

  interface SpectrogramReady {
    ok: true
    timeCols: number
    freqBins: number
    durationMs: number
    sampleRateHz: number
    minHz: number
    maxHz: number
    dbFloor: number
    dbCeil: number
    logFreq: boolean
    /** base64 column-major magnitude matrix, length timeCols*freqBins, unsigned 0..255. */
    data: string
  }

  interface Window {
    jetplay?: {
      mediaUrl?: string
      fileName?: string
      fileExtension?: string
      isVideo?: boolean
      state?: 'loading' | 'ready' | 'error'
      errorMessage?: string
      transcodingReason?: string
      waveform?: number[]
      mediaInfo?: MediaInfo
      ui?: {
        transcodingLabel?: string
        transcodingTip?: string
        errorTitle?: string
      }
    }
    jetplayUpdateProgress?: (percent: number) => void
    jetplayStartTranscoding?: () => void
    jetplayReady?: (mediaUrl: string) => void
    jetplayError?: (message: string) => void
    jetplayWaveform?: (bars: number[]) => void
    __jetplayWaveform?: number[]
    // Buffered state pushes (read on mount so an early transition isn't dropped).
    __jetplayReadyUrl?: string
    __jetplayError?: string
    __jetplayState?: 'loading' | 'ready' | 'error'
    __jetplayProgress?: number
    jetplayMediaInfo?: (info: MediaInfo) => void
    __jetplayMediaInfo?: MediaInfo
    jetplayOpenLink?: (url: string) => void
    // Lazy spectrogram: the page asks via jetplayRequestSpectrogram, the IDE answers via jetplaySpectrogram.
    jetplayRequestSpectrogram?: () => void
    jetplaySpectrogram?: (data: SpectrogramData) => void
    __jetplaySpectrogram?: SpectrogramData
  }
}