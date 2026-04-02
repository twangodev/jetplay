<script lang="ts">
  import { Play, Pause } from '@lucide/svelte'
  import { formatTime } from './formatTime'
  import SeekBar from './SeekBar.svelte'
  import VolumeControl from './VolumeControl.svelte'
  import Branding from './Branding.svelte'

  let { src, fileName }: { src: string; fileName: string } = $props()

  let videoEl: HTMLVideoElement
  let paused = $state(true)
  let currentTime = $state(0)
  let duration = $state(0)
  let volume = $state(1)
  let muted = $state(false)
  let controlsVisible = $state(true)
  let hideTimer: ReturnType<typeof setTimeout>

  function togglePlay() {
    if (videoEl.paused) videoEl.play()
    else videoEl.pause()
  }

  function toggleMute() {
    muted = !muted
    videoEl.muted = muted
  }

  function setVolume(v: number) {
    volume = v
    videoEl.volume = v
    if (v > 0) muted = false
  }

  function showControls() {
    controlsVisible = true
    clearTimeout(hideTimer)
    hideTimer = setTimeout(() => {
      if (!paused) controlsVisible = false
    }, 3000)
  }

  function handleKeydown(e: KeyboardEvent) {
    if (e.code === 'Space') {
      e.preventDefault()
      togglePlay()
    } else if (e.code === 'ArrowLeft') {
      videoEl.currentTime = Math.max(0, videoEl.currentTime - 5)
    } else if (e.code === 'ArrowRight') {
      videoEl.currentTime = Math.min(duration, videoEl.currentTime + 5)
    }
  }
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
  class="flex-1 flex items-center justify-center bg-black min-h-0 overflow-hidden relative cursor-default select-none"
  onmousemove={showControls}
  onmouseenter={showControls}
  onkeydown={handleKeydown}
  tabindex="-1"
>
  <!-- svelte-ignore a11y_media_has_caption -->
  <video
    bind:this={videoEl}
    {src}
    class="max-w-full max-h-full outline-none"
    onclick={togglePlay}
    ontimeupdate={() => (currentTime = videoEl.currentTime)}
    ondurationchange={() => (duration = videoEl.duration)}
    onplay={() => { paused = false; showControls() }}
    onpause={() => { paused = true; controlsVisible = true }}
  ></video>

  <div
    class="absolute bottom-0 left-0 right-0 transition-opacity duration-300"
    class:opacity-0={!controlsVisible}
    class:pointer-events-none={!controlsVisible}
  >
    <div class="bg-gradient-to-t from-black/70 to-transparent pt-10 pb-2 px-3">
      <div class="mb-1">
        <SeekBar
          {currentTime}
          {duration}
          onseek={(t) => (videoEl.currentTime = t)}
          showTime={false}
          trackClass="bg-white/25"
          fillClass="bg-white/70"
        />
      </div>

      <div class="flex items-center gap-3 text-white">
        <button
          class="p-1 hover:opacity-80 transition-opacity bg-transparent border-none cursor-pointer text-white"
          onclick={togglePlay}
        >
          {#if paused}
            <Play size={18} fill="currentColor" />
          {:else}
            <Pause size={18} fill="currentColor" />
          {/if}
        </button>

        <span class="text-xs tabular-nums opacity-80">
          {formatTime(currentTime)} / {formatTime(duration)}
        </span>

        <span class="flex-1 text-xs text-center opacity-50 truncate px-4">
          {fileName}
        </span>

        <VolumeControl
          {volume}
          {muted}
          ontoggle={toggleMute}
          onset={setVolume}
          iconClass="text-white hover:opacity-80"
          trackClass="bg-white/25"
          fillClass="bg-white/80"
          sliderWidth="group-hover/vol:w-16"
        />
      </div>
    </div>
  </div>

  <Branding variant="video" />
</div>