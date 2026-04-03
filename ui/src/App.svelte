<script lang="ts">
  import VideoPlayer from './lib/VideoPlayer.svelte'
  import AudioPlayer from './lib/AudioPlayer.svelte'
  import DownloadingState from './lib/DownloadingState.svelte'
  import TranscodingState from './lib/TranscodingState.svelte'
  import ErrorState from './lib/ErrorState.svelte'

  const config = window.jetplay ?? {}

  let state = $state(config.state ?? 'ready')
  let progress = $state(0)
  let downloadProgress = $state(0)
  let mediaUrl = $state(config.mediaUrl ?? '')
  let errorMessage = $state(config.errorMessage ?? 'An unknown error occurred')

  const fileName = config.fileName ?? 'Unknown'
  const fileExtension = config.fileExtension ?? ''
  const isVideo = config.isVideo ?? false
  const transcodingReason = config.transcodingReason ?? ''
  const downloadingReason = config.downloadingReason ?? ''
  const ui = config.ui ?? {}

  window.jetplayUpdateProgress = (percent: number) => {
    progress = percent
  }

  window.jetplayUpdateDownloadProgress = (percent: number) => {
    downloadProgress = percent
  }

  window.jetplayStartTranscoding = () => {
    progress = 0
    state = 'loading'
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

{#if state === 'downloading'}
  <DownloadingState {fileName} progress={downloadProgress} reason={downloadingReason} downloadingLabel={ui.downloadingLabel} />
{:else if state === 'loading'}
  <TranscodingState {fileName} {progress} reason={transcodingReason} transcodingLabel={ui.transcodingLabel} transcodingTip={ui.transcodingTip} />
{:else if state === 'error'}
  <ErrorState message={errorMessage} errorTitle={ui.errorTitle} />
{:else if isVideo}
  <VideoPlayer src={mediaUrl} {fileName} />
{:else}
  <AudioPlayer src={mediaUrl} {fileName} extension={fileExtension} />
{/if}
