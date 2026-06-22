// Maps an HTML5 MediaError into a concise human-readable detail string.
export function mediaErrorDetail(error: MediaError | null | undefined): string {
  switch (error?.code) {
    case MediaError.MEDIA_ERR_ABORTED:
      return 'Playback was aborted.'
    case MediaError.MEDIA_ERR_NETWORK:
      return 'A network error interrupted the media stream.'
    case MediaError.MEDIA_ERR_DECODE:
      return 'The media could not be decoded; the codec may be unsupported or the stream is corrupt.'
    case MediaError.MEDIA_ERR_SRC_NOT_SUPPORTED:
      return 'This media format or codec is not supported.'
    default:
      return 'An unknown media error occurred.'
  }
}
