<script lang="ts">
  import * as AudioPlayer from '$lib/components/ui/audio-player/index.js'
  import AudioPlayerBody from './AudioPlayerBody.svelte'

  let {
    src,
    fileName,
    extension,
    waveform = [],
    mediaInfo,
    spectrogram,
  }: {
    src: string
    fileName: string
    extension: string
    waveform?: number[]
    mediaInfo?: MediaInfo
    spectrogram?: SpectrogramData
  } = $props()
</script>

<!--
  AudioPlayer.Root owns the <audio> element and the shared player context; the
  controls live in AudioPlayerBody so they can read that context via
  useAudioPlayer(). jetplay opens a single file, so the body feeds it as the one
  track. See AudioPlayerBody.svelte.
-->
<AudioPlayer.Root>
  <AudioPlayerBody {src} {fileName} {extension} {waveform} {mediaInfo} {spectrogram} />
</AudioPlayer.Root>
