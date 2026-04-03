export {}

declare global {
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
    jetplayOpenLink?: (url: string) => void
  }
}