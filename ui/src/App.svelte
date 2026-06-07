<script lang="ts">
  import VideoPlayer from './lib/VideoPlayer.svelte'
  import AudioPlayer from './lib/AudioPlayer.svelte'
  import DownloadingState from './lib/DownloadingState.svelte'
  import TranscodingState from './lib/TranscodingState.svelte'
  import ErrorState from './lib/ErrorState.svelte'

  const config = window.jetplay ?? {}

  // Seed from window stashes: a fast transcode can push state before these handlers mount.
  let state = $state(
    window.__jetplayReadyUrl
      ? 'ready'
      : window.__jetplayError
        ? 'error'
        : (window.__jetplayState ?? config.state ?? 'ready'),
  )
  let progress = $state(window.__jetplayProgress ?? 0)
  let downloadProgress = $state(window.__jetplayDownloadProgress ?? 0)
  let mediaUrl = $state(window.__jetplayReadyUrl ?? config.mediaUrl ?? '')
  let errorMessage = $state(window.__jetplayError ?? config.errorMessage ?? 'An unknown error occurred')
  // Prefer a buffered push: the IDE may have stashed bars before this handler existed.
  let waveform = $state(window.__jetplayWaveform ?? config.waveform ?? [])
  let mediaInfo = $state(window.__jetplayMediaInfo ?? config.mediaInfo)

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

  // Amplitude bars decoded by FFmpeg in the IDE, cheaper than decoding the whole file in-browser.
  window.jetplayWaveform = (bars: number[]) => {
    waveform = bars
  }

  // FFmpeg-probed technical metadata pushed from the IDE for the codec inspector.
  window.jetplayMediaInfo = (info: MediaInfo) => {
    mediaInfo = info
  }
</script>

{#if state === 'downloading'}
  <DownloadingState {fileName} progress={downloadProgress} reason={downloadingReason} downloadingLabel={ui.downloadingLabel} />
{:else if state === 'loading'}
  <TranscodingState {fileName} {progress} reason={transcodingReason} transcodingLabel={ui.transcodingLabel} transcodingTip={ui.transcodingTip} />
{:else if state === 'error'}
  <ErrorState message={errorMessage} errorTitle={ui.errorTitle} />
{:else if isVideo}
  <VideoPlayer src={mediaUrl} {fileName} extension={fileExtension} {mediaInfo} />
{:else}
  <AudioPlayer src={mediaUrl} {fileName} extension={fileExtension} {waveform} {mediaInfo} />
{/if}
