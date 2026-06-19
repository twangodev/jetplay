<script lang="ts">
  import WindowFrame from './WindowFrame.svelte'
  import AudioPlayer from '$lib/AudioPlayer.svelte'

  const config = window.jetplay ?? {}
</script>

<!-- No `waveform` prop: AudioPlayerBody decodes the MP3 in-browser (precomputeWaveform) — the real live waveform. -->
<div
  class="flex h-full w-full items-center justify-center p-16
         bg-[radial-gradient(circle_at_50%_30%,var(--color-stage),var(--color-stage-deep))]"
>
  <WindowFrame title={config.fileName ?? 'sneaky-snitch.mp3'}>
    <AudioPlayer
      src={config.mediaUrl}
      fileName={config.fileName}
      extension={config.fileExtension}
      mediaInfo={config.mediaInfo}
    />
  </WindowFrame>
</div>
