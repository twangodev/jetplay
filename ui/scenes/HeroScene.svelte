<script lang="ts">
  import WindowFrame from './WindowFrame.svelte'
  import AudioPlayer from '$lib/AudioPlayer.svelte'

  const config = window.jetplay ?? {}
</script>

<div class="flex h-full w-full items-center justify-between gap-12 bg-night px-16 font-grotesk text-night-ink">
  <div class="w-[480px] shrink-0">
    <div class="font-jbmono text-sm tracking-wider text-night-muted">jetplay, a jetbrains ide plugin</div>

    <h1 class="mt-4 text-6xl leading-[1.05] font-semibold tracking-tight text-night-ink">
      Play media without leaving your editor.
    </h1>

    <div class="mt-8 flex flex-wrap items-center gap-2 font-jbmono text-xs tracking-wider text-night-muted">
      <span class="rounded-full border border-night-subtle px-3 py-1">open source</span>
      <span class="rounded-full border border-night-subtle px-3 py-1">remote dev</span>
    </div>
  </div>

  <div class="flex shrink-0 items-center">
    <WindowFrame title={config.fileName ?? 'sneaky-snitch.mp3'} width={580}>
      <AudioPlayer
        src={config.mediaUrl}
        fileName={config.fileName}
        extension={config.fileExtension}
        mediaInfo={config.mediaInfo}
      />
    </WindowFrame>
  </div>
</div>
