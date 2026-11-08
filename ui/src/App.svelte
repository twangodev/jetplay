<script lang="ts">
  import VideoPlayer from './lib/VideoPlayer.svelte'
  import AudioPlayer from './lib/AudioPlayer.svelte'
  import TranscodingState from './lib/TranscodingState.svelte'
  import ErrorState from './lib/ErrorState.svelte'
  import { mediaErrorDetail } from './lib/mediaError'

  const config = window.jetplay ?? {}

  // Seed from window stashes: a fast transcode can push state before these handlers mount.
  let playbackState = $state(
    window.__jetplayReadyUrl
      ? 'ready'
      : window.__jetplayError
        ? 'error'
        : (window.__jetplayState ?? config.state ?? 'ready'),
  )
  let progress = $state(window.__jetplayProgress ?? 0)
  let mediaUrl = $state(window.__jetplayReadyUrl ?? config.mediaUrl ?? '')
  let errorMessage = $state(window.__jetplayError ?? config.errorMessage ?? 'An unknown error occurred')
  // Prefer a buffered push: the IDE may have stashed bars before this handler existed.
  let waveform = $state(window.__jetplayWaveform ?? config.waveform ?? [])
  let mediaInfo = $state(window.__jetplayMediaInfo ?? config.mediaInfo)
  // Lazy: undefined until the user reveals the spectrogram and the IDE answers.
  let spectrogram = $state(window.__jetplaySpectrogram)

  const fileName = config.fileName ?? 'Unknown'
  const filePath = config.filePath || fileName
  const fileExtension = config.fileExtension ?? ''
  const isVideo = config.isVideo ?? false
  const transcodingReason = config.transcodingReason ?? ''
  const ui = config.ui ?? {}

  window.jetplayUpdateProgress = (percent: number) => {
    progress = percent
  }

  window.jetplayStartTranscoding = () => {
    progress = 0
    playbackState = 'loading'
  }

  window.jetplayReady = (url: string) => {
    mediaUrl = url
    playbackState = 'ready'
  }

  window.jetplayError = (message: string) => {
    errorMessage = message
    playbackState = 'error'
  }

  // Surfaces HTML5 media element failures through the same error screen.
  function onMediaError(error: MediaError | null) {
    errorMessage = mediaErrorDetail(error)
    playbackState = 'error'
  }

  // Amplitude bars decoded by FFmpeg in the IDE, cheaper than decoding the whole file in-browser.
  window.jetplayWaveform = (bars: number[]) => {
    waveform = bars
  }

  // FFmpeg-probed technical metadata pushed from the IDE for the codec inspector.
  window.jetplayMediaInfo = (info: MediaInfo) => {
    mediaInfo = info
  }

  // Answer to a lazy spectrogram request (the matrix, or { ok: false } when there's nothing to show).
  window.jetplaySpectrogram = (data: SpectrogramData) => {
    spectrogram = data
  }
</script>

{#if playbackState === 'loading'}
  <TranscodingState {fileName} {progress} reason={transcodingReason} transcodingLabel={ui.transcodingLabel} transcodingTip={ui.transcodingTip} />
{:else if playbackState === 'error'}
  <ErrorState message={errorMessage} errorTitle={ui.errorTitle} />
{:else if isVideo}
  <VideoPlayer src={mediaUrl} {fileName} {filePath} extension={fileExtension} {mediaInfo} {onMediaError} />
{:else}
  <AudioPlayer src={mediaUrl} {fileName} {filePath} extension={fileExtension} {waveform} {mediaInfo} {spectrogram} />
{/if}
