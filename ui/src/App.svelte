<script lang="ts">
  import VideoPlayer from './lib/VideoPlayer.svelte'
  import AudioPlayer from './lib/AudioPlayer.svelte'
  import DownloadingState from './lib/DownloadingState.svelte'
  import TranscodingState from './lib/TranscodingState.svelte'
  import ErrorState from './lib/ErrorState.svelte'

  const config = window.jetplay ?? {}

  // The IDE may push a state transition (mediaReady/error/progress) before these
  // handlers exist — a fast transcode can finish before the page mounts — in
  // which case the call is a silent no-op. It stashes the latest on window, so
  // we seed from the stash here to avoid getting stuck on the loading screen.
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
  // Prefer a buffered push (the IDE may have called jetplayWaveform before this
  // handler existed, in which case it stashed the bars on window).
  let waveform = $state(window.__jetplayWaveform ?? config.waveform ?? [])
  // Same buffered-push pattern for the codec inspector metadata.
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

  // FFmpeg-decoded amplitude bars pushed from the IDE (cheaper than decoding the
  // whole file in the browser).
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
