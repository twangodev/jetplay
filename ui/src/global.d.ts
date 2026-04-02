export {}

declare global {
  interface Window {
    jetplay?: {
      mediaUrl?: string
      fileName?: string
      fileExtension?: string
      isVideo?: boolean
      state?: 'loading' | 'ready' | 'error'
      errorMessage?: string
      transcodingReason?: string
    }
    jetplayUpdateProgress?: (percent: number) => void
    jetplayReady?: (mediaUrl: string) => void
    jetplayError?: (message: string) => void
    jetplayOpenLink?: (url: string) => void
  }
}