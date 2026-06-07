export {}

declare global {
  // Technical metadata for the codec inspector. Every field is optional — the
  // IDE omits anything FFmpeg couldn't determine, and the UI skips it.
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
  }

  interface Window {
    jetplay?: {
      mediaUrl?: string
      fileName?: string
      fileExtension?: string
      isVideo?: boolean
      state?: 'downloading' | 'loading' | 'ready' | 'error'
      errorMessage?: string
      transcodingReason?: string
      downloadingReason?: string
      waveform?: number[]
      mediaInfo?: MediaInfo
      ui?: {
        downloadingLabel?: string
        transcodingLabel?: string
        transcodingTip?: string
        errorTitle?: string
      }
    }
    jetplayUpdateProgress?: (percent: number) => void
    jetplayUpdateDownloadProgress?: (percent: number) => void
    jetplayStartTranscoding?: () => void
    jetplayReady?: (mediaUrl: string) => void
    jetplayError?: (message: string) => void
    jetplayWaveform?: (bars: number[]) => void
    __jetplayWaveform?: number[]
    jetplayMediaInfo?: (info: MediaInfo) => void
    __jetplayMediaInfo?: MediaInfo
    jetplayOpenLink?: (url: string) => void
  }
}