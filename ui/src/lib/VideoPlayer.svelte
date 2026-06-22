<script lang="ts">
  import { onMount } from 'svelte'
  import { slide } from 'svelte/transition'
  import { Slider as SliderPrimitive } from 'bits-ui'
  import {
    ChevronDown,
    Play,
    Pause,
    SkipBack,
    SkipForward,
    StepBack,
    StepForward,
    Volume,
    Volume1,
    Volume2,
    VolumeX,
    Gauge,
    Check,
  } from '@lucide/svelte'
  import * as DropdownMenu from '$lib/components/ui/dropdown-menu/index.js'
  import { cn } from '$lib/utils.js'
  import { formatTime } from './formatTime'
  import { videoInfoRows, audioInfoRows, generalInfoRows } from './mediaInfoRows'

  let {
    src,
    fileName,
    extension = '',
    mediaInfo,
    onMediaError,
  }: {
    src: string
    fileName: string
    extension?: string
    mediaInfo?: MediaInfo
    onMediaError?: (error: MediaError | null) => void
  } = $props()

  let containerEl: HTMLDivElement
  let videoEl: HTMLVideoElement
  let paused = $state(true)
  let currentTime = $state(0)
  let duration = $state(0)
  let volume = $state(1)
  let muted = $state(false)
  let playbackRate = $state(1)
  let controlsVisible = $state(true)
  let infoExpanded = $state(false)
  let hideTimer: ReturnType<typeof setTimeout>

  const PLAYBACK_SPEEDS = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2]
  const btn =
    'text-white/90 hover:text-white transition-colors outline-none focus-visible:ring-2 focus-visible:ring-white/60 rounded p-1 disabled:opacity-40'

  const VolumeIcon = $derived(
    muted || volume === 0 ? VolumeX : volume <= 0.33 ? Volume : volume <= 0.66 ? Volume1 : Volume2,
  )

  const videoRows = $derived(videoInfoRows(mediaInfo))
  const audioRows = $derived(audioInfoRows(mediaInfo))
  const generalRows = $derived(generalInfoRows(mediaInfo))
  const tags = $derived(mediaInfo?.tags ?? [])
  const hasMediaInfo = $derived(videoRows.length + audioRows.length + generalRows.length + tags.length > 0)

  const seekMax = $derived(Number.isFinite(duration) && duration > 0 ? duration : 0)

  function togglePlay() {
    if (videoEl.paused) void videoEl.play()
    else videoEl.pause()
  }
  function toggleMute() {
    muted = !muted
    videoEl.muted = muted
  }
  function setVolume(v: number) {
    volume = v
    videoEl.volume = v
    if (v > 0) {
      muted = false
      videoEl.muted = false
    }
  }
  function seek(t: number) {
    if (seekMax > 0) videoEl.currentTime = Math.max(0, Math.min(seekMax, t))
  }
  function skip(delta: number) {
    seek(videoEl.currentTime + delta)
  }
  function frameStep(dir: number) {
    videoEl.pause()
    const fps = mediaInfo?.frameRate && mediaInfo.frameRate > 0 ? mediaInfo.frameRate : 30
    seek(videoEl.currentTime + dir / fps)
  }
  function setSpeed(r: number) {
    playbackRate = r
    videoEl.playbackRate = r
  }

  // Without this guard bits-ui re-snaps the off-grid currentTime back onto the
  // step and re-fires onValueChange every timeupdate, re-seeking each frame.
  let seekInteracting = false
  let resumeAfterScrub = false

  function onSeekChange(v: number) {
    if (seekInteracting) seek(v)
  }
  function onSeekPointerDown() {
    seekInteracting = true
    resumeAfterScrub = !videoEl.paused
    videoEl.pause()
    // bits-ui releases the pointer on `document`, so listen on window or the guard sticks open.
    window.addEventListener('pointerup', endSeekScrub)
    window.addEventListener('pointercancel', endSeekScrub)
  }
  function endSeekScrub() {
    seekInteracting = false
    window.removeEventListener('pointerup', endSeekScrub)
    window.removeEventListener('pointercancel', endSeekScrub)
    if (resumeAfterScrub) {
      resumeAfterScrub = false
      void videoEl.play()
    }
  }
  // Capture-phase (registered in onMount) so we seek before bits-ui's thumb moves
  // off-grid, which the seek guard would suppress and desync.
  function onSeekKeyCapture(e: KeyboardEvent) {
    const target = e.target as HTMLElement | null
    if (!target?.closest('[data-seek-slider]')) return
    const handle = (fn: () => void) => {
      e.preventDefault()
      e.stopPropagation()
      fn()
    }
    switch (e.code) {
      case 'Space':
        return handle(togglePlay)
      case 'ArrowLeft':
      case 'ArrowDown':
        return handle(() => skip(-5))
      case 'ArrowRight':
      case 'ArrowUp':
        return handle(() => skip(5))
      case 'PageDown':
        return handle(() => skip(-10))
      case 'PageUp':
        return handle(() => skip(10))
      case 'Home':
        return handle(() => seek(0))
      case 'End':
        return handle(() => seek(seekMax))
    }
  }

  function showControls() {
    controlsVisible = true
    clearTimeout(hideTimer)
    hideTimer = setTimeout(() => {
      if (!paused && !infoExpanded) controlsVisible = false
    }, 3000)
  }

  function handleKeydown(e: KeyboardEvent) {
    const innerControlFocused = e.target !== containerEl
    if (innerControlFocused) return
    if (e.code === 'Space') {
      e.preventDefault()
      togglePlay()
    } else if (e.code === 'ArrowLeft') {
      e.preventDefault()
      skip(-5)
    } else if (e.code === 'ArrowRight') {
      e.preventDefault()
      skip(5)
    } else if (e.code === 'KeyM') {
      e.preventDefault()
      toggleMute()
    }
  }

  onMount(() => {
    containerEl?.focus()
    containerEl.addEventListener('keydown', onSeekKeyCapture, { capture: true })
    return () => containerEl.removeEventListener('keydown', onSeekKeyCapture, { capture: true })
  })
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
  bind:this={containerEl}
  class="relative flex-1 flex items-center justify-center bg-black min-h-0 overflow-hidden select-none outline-none"
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
    onclick={() => {
      togglePlay()
      containerEl.focus()
    }}
    ontimeupdate={() => (currentTime = videoEl.currentTime)}
    ondurationchange={() => (duration = Number.isFinite(videoEl.duration) ? videoEl.duration : 0)}
    onloadedmetadata={() => (duration = Number.isFinite(videoEl.duration) ? videoEl.duration : 0)}
    onplay={() => {
      paused = false
      showControls()
    }}
    onpause={() => {
      paused = true
      controlsVisible = true
    }}
    onerror={() => onMediaError?.(videoEl.error)}
  ></video>

  {@render topBar()}
  {@render inspectorPanel()}
  {@render bottomControls()}
</div>

{#snippet titleRow()}
  <span class="shrink-0 text-sm font-medium text-white/50">jetplay :</span>
  <span class="truncate text-sm font-medium">{fileName}</span>
  {#if extension}
    <span class="shrink-0 rounded-sm border border-white/30 px-1.5 py-0.5 text-[11px] font-medium tracking-wide uppercase">
      {extension}
    </span>
  {/if}
{/snippet}

{#snippet topBar()}
  <div
    class="absolute top-0 right-0 left-0 transition-opacity duration-300"
    class:opacity-0={!controlsVisible}
    class:pointer-events-none={!controlsVisible}
  >
    <div class="bg-gradient-to-b from-black/70 to-transparent px-3 pt-2 pb-8">
      {#if hasMediaInfo}
        <button
          type="button"
          class="flex w-full items-center gap-2 rounded-sm text-left text-white outline-none focus-visible:ring-2 focus-visible:ring-white/60"
          aria-expanded={infoExpanded}
          aria-controls="video-info-panel"
          aria-label="Toggle media details"
          onclick={() => (infoExpanded = !infoExpanded)}
        >
          {@render titleRow()}
          <ChevronDown class={cn('ml-auto size-4 shrink-0 transition-transform', infoExpanded && 'rotate-180')} />
        </button>
      {:else}
        <div class="flex items-center gap-2 text-white">
          {@render titleRow()}
        </div>
      {/if}
    </div>
  </div>
{/snippet}

{#snippet inspectorPanel()}
  {#if hasMediaInfo && infoExpanded}
    {@const group = 'grid grid-cols-[auto_1fr] gap-x-4 gap-y-1'}
    <div
      id="video-info-panel"
      data-slot="media-info-panel"
      transition:slide={{ duration: 200 }}
      class="absolute top-11 left-3 z-10 max-h-[70%] w-72 max-w-[80%] overflow-auto rounded-lg bg-black/80 p-3 text-xs text-white backdrop-blur-sm"
    >
      {#if videoRows.length}
        <div data-slot="video-info-group" class={group}>
          {#each videoRows as r (r.label)}
            <span class="text-white/60">{r.label}</span>
            <span class="font-mono break-words">{r.value}</span>
          {/each}
        </div>
      {/if}
      {#if audioRows.length}
        <div class={cn(group, videoRows.length && 'mt-2 border-t border-white/10 pt-2')}>
          {#each audioRows as r (r.label)}
            <span class="text-white/60">{r.label}</span>
            <span class="font-mono break-words">{r.value}</span>
          {/each}
        </div>
      {/if}
      {#if generalRows.length}
        <div class={cn(group, (videoRows.length || audioRows.length) && 'mt-2 border-t border-white/10 pt-2')}>
          {#each generalRows as r (r.label)}
            <span class="text-white/60">{r.label}</span>
            <span class="font-mono break-words">{r.value}</span>
          {/each}
        </div>
      {/if}
      {#if tags.length}
        <div class={cn(group, (videoRows.length || audioRows.length || generalRows.length) && 'mt-2 border-t border-white/10 pt-2')}>
          {#each tags as t (t.label)}
            <span class="text-white/60">{t.label}</span>
            <span class="break-words">{t.value}</span>
          {/each}
        </div>
      {/if}
    </div>
  {/if}
{/snippet}

{#snippet bottomControls()}
  <div
    class="absolute right-0 bottom-0 left-0 transition-opacity duration-300"
    class:opacity-0={!controlsVisible}
    class:pointer-events-none={!controlsVisible}
  >
    <div class="bg-gradient-to-t from-black/70 to-transparent px-3 pt-10 pb-2 text-white">
      {@render seekBar()}
      <div class="mt-1 flex items-center gap-1.5">
        {@render transportButtons()}
        <span class="ml-1 text-xs tabular-nums opacity-80">
          {formatTime(currentTime)} / {formatTime(duration)}
        </span>
        {@render volumeControl()}
        <span class="flex-1"></span>
        {@render speedMenu()}
      </div>
    </div>
  </div>
{/snippet}

{#snippet seekBar()}
  <SliderPrimitive.Root
    type="single"
    value={currentTime}
    min={0}
    max={seekMax}
    step={0.1}
    aria-label="Seek"
    data-seek-slider
    onValueChange={onSeekChange}
    onpointerdown={onSeekPointerDown}
    class="group/seek relative flex h-4 w-full touch-none items-center select-none"
  >
    {#snippet children({ thumbItems })}
      <span class="relative h-[3px] w-full grow overflow-hidden rounded-full bg-white/25">
        <SliderPrimitive.Range class="absolute h-full bg-white" />
      </span>
      {#each thumbItems as thumb (thumb.index)}
        <SliderPrimitive.Thumb
          index={thumb.index}
          class="block size-3 rounded-full bg-white opacity-0 transition-opacity group-hover/seek:opacity-100 focus-visible:opacity-100 focus-visible:outline-none"
        />
      {/each}
    {/snippet}
  </SliderPrimitive.Root>
{/snippet}

{#snippet transportButtons()}
  <button class={btn} onclick={() => skip(-10)} aria-label="Back 10 seconds">
    <SkipBack class="size-4" />
  </button>
  <button class={btn} onclick={() => frameStep(-1)} aria-label="Previous frame">
    <StepBack class="size-4" />
  </button>
  <button class={btn} onclick={togglePlay} aria-label={paused ? 'Play' : 'Pause'}>
    {#if paused}
      <Play class="size-5" fill="currentColor" />
    {:else}
      <Pause class="size-5" fill="currentColor" />
    {/if}
  </button>
  <button class={btn} onclick={() => frameStep(1)} aria-label="Next frame">
    <StepForward class="size-4" />
  </button>
  <button class={btn} onclick={() => skip(10)} aria-label="Forward 10 seconds">
    <SkipForward class="size-4" />
  </button>
{/snippet}

{#snippet volumeControl()}
  <div class="ml-2 flex items-center gap-1.5">
    <button class={btn} onclick={toggleMute} aria-label="Toggle mute">
      <VolumeIcon class="size-4" />
    </button>
    <SliderPrimitive.Root
      type="single"
      value={muted ? 0 : volume}
      min={0}
      max={1}
      step={0.01}
      aria-label="Volume"
      onValueChange={(v) => setVolume(v)}
      class="group/vol relative flex h-4 w-16 touch-none items-center select-none"
    >
      {#snippet children({ thumbItems })}
        <span class="relative h-[3px] w-full grow overflow-hidden rounded-full bg-white/25">
          <SliderPrimitive.Range class="absolute h-full bg-white" />
        </span>
        {#each thumbItems as thumb (thumb.index)}
          <SliderPrimitive.Thumb
            index={thumb.index}
            class="block size-3 rounded-full bg-white opacity-0 transition-opacity group-hover/vol:opacity-100 focus-visible:opacity-100 focus-visible:outline-none"
          />
        {/each}
      {/snippet}
    </SliderPrimitive.Root>
  </div>
{/snippet}

{#snippet speedMenu()}
  <DropdownMenu.Root>
    <DropdownMenu.Trigger>
      {#snippet child({ props })}
        <button {...props} class={btn} aria-label="Playback speed">
          <Gauge class="size-4" />
        </button>
      {/snippet}
    </DropdownMenu.Trigger>
    <DropdownMenu.Content align="end" class="min-w-[120px]">
      {#each PLAYBACK_SPEEDS as speed (speed)}
        <DropdownMenu.Item onclick={() => setSpeed(speed)} class="flex items-center justify-between">
          <span class={speed === 1 ? '' : 'font-mono'}>{speed === 1 ? 'Normal' : `${speed}x`}</span>
          {#if playbackRate === speed}
            <Check class="size-4" />
          {/if}
        </DropdownMenu.Item>
      {/each}
    </DropdownMenu.Content>
  </DropdownMenu.Root>
{/snippet}
