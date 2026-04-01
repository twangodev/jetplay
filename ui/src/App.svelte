<script lang="ts">
  import VideoPlayer from './lib/VideoPlayer.svelte'
  import AudioPlayer from './lib/AudioPlayer.svelte'
  import LoadingState from './lib/LoadingState.svelte'
  import ErrorState from './lib/ErrorState.svelte'

  // The Kotlin side sets these on window before loading
  declare global {
    interface Window {
      jetplay?: {
        mediaUrl?: string
        fileName?: string
        fileExtension?: string
        isVideo?: boolean
        state?: 'loading' | 'ready' | 'error'
        errorMessage?: string
      }
    }
  }

  const config = window.jetplay ?? {}
  const state = config.state ?? 'ready'
  const mediaUrl = config.mediaUrl ?? ''
  const fileName = config.fileName ?? 'Unknown'
  const fileExtension = config.fileExtension ?? ''
  const isVideo = config.isVideo ?? false
  const errorMessage = config.errorMessage ?? 'An unknown error occurred'
</script>

{#if state === 'loading'}
  <LoadingState {fileName} />
{:else if state === 'error'}
  <ErrorState message={errorMessage} />
{:else if isVideo}
  <VideoPlayer src={mediaUrl} />
{:else}
  <AudioPlayer src={mediaUrl} {fileName} extension={fileExtension} />
{/if}