<script lang="ts">
  import VideoPlayer from './lib/VideoPlayer.svelte'
  import AudioPlayer from './lib/AudioPlayer.svelte'
  import TranscodingState from './lib/TranscodingState.svelte'
  import ErrorState from './lib/ErrorState.svelte'

  const config = window.jetplay ?? {}

  let state = $state(config.state ?? 'ready')
  let progress = $state(0)
  let mediaUrl = $state(config.mediaUrl ?? '')
  let errorMessage = $state(config.errorMessage ?? 'An unknown error occurred')

  const fileName = config.fileName ?? 'Unknown'
  const fileExtension = config.fileExtension ?? ''
  const isVideo = config.isVideo ?? false
  const transcodingReason = config.transcodingReason ?? ''

  window.jetplayUpdateProgress = (percent: number) => {
    progress = percent
  }

  window.jetplayReady = (url: string) => {
    mediaUrl = url
    state = 'ready'
  }

  window.jetplayError = (message: string) => {
    errorMessage = message
    state = 'error'
  }
</script>

{#if state === 'loading'}
  <TranscodingState {fileName} {progress} reason={transcodingReason} />
{:else if state === 'error'}
  <ErrorState message={errorMessage} />
{:else if isVideo}
  <VideoPlayer src={mediaUrl} {fileName} />
{:else}
  <AudioPlayer src={mediaUrl} {fileName} extension={fileExtension} />
{/if}
