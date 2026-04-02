<script lang="ts">
  import { Music, Play, Pause, SkipBack, SkipForward } from '@lucide/svelte'
  import SeekBar from './SeekBar.svelte'
  import VolumeControl from './VolumeControl.svelte'
  import Branding from './Branding.svelte'

  let { src, fileName, extension }: { src: string; fileName: string; extension: string } = $props()

  let audioEl: HTMLAudioElement
  let paused = $state(true)
  let currentTime = $state(0)
  let duration = $state(0)
  let volume = $state(1)
  let muted = $state(false)

  function togglePlay() {
    if (audioEl.paused) audioEl.play()
    else audioEl.pause()
  }

  function skipBack() {
    audioEl.currentTime = Math.max(0, audioEl.currentTime - 10)
  }

  function skipForward() {
    audioEl.currentTime = Math.min(duration, audioEl.currentTime + 10)
  }

  function toggleMute() {
    muted = !muted
    audioEl.muted = muted
  }

  function setVolume(v: number) {
    volume = v
    audioEl.volume = v
    if (v > 0) muted = false
  }

  function handleKeydown(e: KeyboardEvent) {
    if (e.code === 'Space') {
      e.preventDefault()
      togglePlay()
    } else if (e.code === 'ArrowLeft') {
      skipBack()
    } else if (e.code === 'ArrowRight') {
      skipForward()
    }
  }
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
  class="flex-1 flex flex-col items-center justify-center gap-5 select-none p-8"
  onkeydown={handleKeydown}
  tabindex="-1"
>
  <audio
    bind:this={audioEl}
    {src}
    ontimeupdate={() => (currentTime = audioEl.currentTime)}
    ondurationchange={() => (duration = audioEl.duration)}
    onplay={() => (paused = false)}
    onpause={() => (paused = true)}
  ></audio>

  <!-- Artwork -->
  <div class="w-20 h-20 rounded-2xl bg-elevated border border-border flex items-center justify-center text-muted shadow-md">
    <Music size={36} strokeWidth={1.5} />
  </div>

  <!-- Metadata -->
  <div class="flex items-center gap-2 max-w-[400px]">
    <span class="text-[15px] font-medium text-primary break-words leading-snug">
      {fileName}
    </span>
    {#if extension}
      <span class="shrink-0 text-[11px] font-medium tracking-wide uppercase text-muted border border-border px-1.5 py-0.5 rounded">
        {extension}
      </span>
    {/if}
  </div>

  <!-- Seek bar -->
  <div class="w-full max-w-md">
    <SeekBar
      {currentTime}
      {duration}
      onseek={(t) => (audioEl.currentTime = t)}
    />
  </div>

  <!-- Transport controls -->
  <div class="flex items-center gap-6">
    <button
      class="p-1.5 text-muted hover:text-primary transition-colors bg-transparent border-none cursor-pointer"
      onclick={skipBack}
    >
      <SkipBack size={20} />
    </button>
    <button
      class="w-11 h-11 rounded-full bg-primary text-surface flex items-center justify-center hover:opacity-80 transition-opacity border-none cursor-pointer"
      onclick={togglePlay}
    >
      {#if paused}
        <Play size={20} fill="currentColor" class="ml-0.5" />
      {:else}
        <Pause size={20} fill="currentColor" />
      {/if}
    </button>
    <button
      class="p-1.5 text-muted hover:text-primary transition-colors bg-transparent border-none cursor-pointer"
      onclick={skipForward}
    >
      <SkipForward size={20} />
    </button>
  </div>

  <!-- Volume -->
  <VolumeControl
    {volume}
    {muted}
    ontoggle={toggleMute}
    onset={setVolume}
  />

  <Branding />
</div>