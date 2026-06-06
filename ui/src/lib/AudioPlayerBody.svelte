<script lang="ts">
  import { onMount, untrack } from 'svelte'
  import { slide } from 'svelte/transition'
  import { SkipBack, SkipForward } from '@lucide/svelte'
  import {
    AudioGraph,
    AudioPlayerButton,
    AudioPlayerDuration,
    AudioPlayerProgress,
    AudioPlayerTime,
    precomputeWaveform,
    useAudioPlayer,
  } from '$lib/components/ui/audio-player/index.js'
  import { Waveform } from '$lib/components/ui/waveform/index.js'
  import VolumeControl from './VolumeControl.svelte'
  import Branding from './Branding.svelte'
  import { useScratchableWaveform } from './use-scratchable-waveform.svelte.js'

  let {
    src,
    fileName,
    extension,
    waveform = [],
  }: { src: string; fileName: string; extension: string; waveform?: number[] } = $props()

  const player = useAudioPlayer<{ name: string }>()
  // Owns the scratch AudioContext used by the scrubbing interaction (no analyser —
  // jetplay has no orb visualizers).
  const graph = new AudioGraph()

  const BARS_PER_SECOND = 8
  const BAR_STEP = 5 // px of scroll travel per waveform bar
  const WAVEFORM_MAX_SECONDS = 20 * 60 // skip the full-file decode past this

  // The IDE pushes FFmpeg-decoded bars via the `waveform` prop (the browser
  // can't read file:// bytes itself). `decodedWaveform` is the in-browser
  // fallback used only when no bars were provided (HTTP contexts / tests).
  let decodedWaveform = $state<number[]>([])
  const precomputedWaveform = $derived(waveform.length > 0 ? waveform : decodedWaveform)
  let waveformContainerEl = $state<HTMLDivElement | null>(null)
  let containerEl: HTMLDivElement | null = $state(null)
  let containerWidth = $state(300)
  const totalWidth = $derived(precomputedWaveform.length * BAR_STEP)
  // Short waveforms stretch to fill the box; longer ones scroll past the playhead.
  const displayWidth = $derived(Math.max(totalWidth, containerWidth))
  const hasWaveform = $derived(precomputedWaveform.length > 0)

  // Volume lives in jetplay's VolumeControl (sv11 has no standalone volume component).
  let volume = $state(1)
  let muted = $state(false)

  // speaker-01's scratchable-waveform interaction: drag to scrub, momentum on
  // release, scratch audio while dragging, keyboard seek.
  const scrub = useScratchableWaveform({
    player,
    graph,
    trackUrl: () => src || null,
    totalWidth: () => displayWidth,
    containerWidth: () => containerWidth,
  })

  const scrubberValue = $derived.by(() => {
    const a = player.audio
    if (!a || !isFinite(a.duration) || a.duration <= 0) return 0
    return Math.round((a.currentTime / a.duration) * 100)
  })

  // Feed jetplay's single opened file as the one track.
  $effect(() => {
    if (!player.audio || !src) return
    untrack(() => {
      void player.setActiveItem({ id: fileName, src, data: { name: fileName } })
    })
  })

  // Fallback only: decode in-browser when the IDE didn't supply bars (capped).
  let waveformStarted = false
  $effect(() => {
    const dur = player.duration
    if (waveform.length > 0 || !src || waveformStarted) return
    if (dur === undefined || !Number.isFinite(dur)) return
    waveformStarted = true
    if (dur > WAVEFORM_MAX_SECONDS) return
    precomputeWaveform(src, BARS_PER_SECOND)
      .then((bars) => {
        decodedWaveform = bars
      })
      .catch(() => {
        /* decode failed (codec/CORS) — bars come from the IDE instead */
      })
  })

  // Track the scroll container's width for the offset math. Re-runs when the
  // waveform box mounts (it only exists once there are bars).
  $effect(() => {
    const el = waveformContainerEl
    if (!el) return
    const measure = () => {
      containerWidth = el.getBoundingClientRect().width
    }
    measure()
    window.addEventListener('resize', measure)
    return () => window.removeEventListener('resize', measure)
  })

  // Drive the scroll offset from playback position, unless the user is scrubbing.
  // Playhead is pinned at the left edge: offset 0 = start, -displayWidth = end.
  $effect(() => {
    let id: number
    const update = () => {
      if (!scrub.isScrubbing && !scrub.isMomentumActive) {
        const a = player.audio
        if (a && !isNaN(a.duration) && a.duration > 0) {
          scrub.offset = -(a.currentTime / a.duration) * displayWidth
        }
      }
      id = requestAnimationFrame(update)
    }
    id = requestAnimationFrame(update)
    return () => cancelAnimationFrame(id)
  })

  // Tear down the scratch audio context on unmount.
  $effect(() => {
    if (!player.audio) return
    return () => {
      graph.destroy()
      scrub.reset()
    }
  })

  // Mirror volume/mute onto the shared <audio>.
  $effect(() => {
    if (player.audio) {
      player.audio.volume = volume
      player.audio.muted = muted
    }
  })

  onMount(() => containerEl?.focus())

  function toggleMute() {
    muted = !muted
  }
  function setVolume(v: number) {
    volume = v
    if (v > 0) muted = false
  }
  function skipBack() {
    const a = player.audio
    if (a) a.currentTime = Math.max(0, a.currentTime - 10)
  }
  function skipForward() {
    const a = player.audio
    if (a && isFinite(a.duration)) a.currentTime = Math.min(a.duration, a.currentTime + 10)
  }
  function handleKeydown(e: KeyboardEvent) {
    if (e.code === 'Space') {
      e.preventDefault()
      // Decide from the live DOM `paused` (always current) to avoid lagged state.
      if (player.audio?.paused) void player.play()
      else void player.pause()
    } else if ((e.code === 'ArrowLeft' || e.code === 'ArrowRight') && e.target === containerEl) {
      // Only when the chrome (not the waveform, which seeks itself) is focused.
      e.preventDefault()
      if (e.code === 'ArrowLeft') skipBack()
      else skipForward()
    }
  }
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
  bind:this={containerEl}
  class="flex-1 flex flex-col justify-center gap-5 select-none p-8 outline-none"
  onkeydown={handleKeydown}
  tabindex="-1"
>
  <!-- Metadata -->
  <div class="flex items-center justify-center gap-2 max-w-[400px] mx-auto">
    <span class="text-[15px] font-medium text-primary break-words leading-snug text-center">
      {fileName}
    </span>
    {#if extension}
      <span class="shrink-0 text-[11px] font-medium tracking-wide uppercase text-muted border border-border px-1.5 py-0.5 rounded">
        {extension}
      </span>
    {/if}
  </div>

  <!-- Scrolling / scratchable waveform (drag to scrub). It only mounts once
       decoding produces bars, and slides in — so before then the layout stays
       compact and the title isn't floating above an empty reserved box. -->
  {#if hasWaveform}
    <div class="w-full max-w-md mx-auto" transition:slide={{ duration: 450 }}>
      <!-- svelte-ignore a11y_no_static_element_interactions -->
      <div
        bind:this={waveformContainerEl}
        class="relative h-16 cursor-grab touch-none overflow-hidden rounded-lg bg-foreground/10 p-2 select-none active:cursor-grabbing outline-none"
        role="slider"
        tabindex="0"
        aria-label="Seek playback"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={scrubberValue}
        onpointerdown={scrub.handlePointerDown}
        onkeydown={scrub.handleKeyDown}
      >
        <div class="relative h-full w-full overflow-hidden">
          <div
            style:transform="translateX({scrub.offset}px)"
            style:transition={scrub.isScrubbing || scrub.isMomentumActive ? 'none' : 'transform 0.016s linear'}
            style:width="{displayWidth}px"
            style:position="absolute"
            style:left="0"
          >
            <Waveform data={precomputedWaveform} height={48} barWidth={3} barGap={2} barRadius={1} fadeEdges={true} fadeWidth={24} />
          </div>
          <!-- playhead -->
          <div class="absolute inset-y-0 left-0 w-0.5 rounded-full bg-primary/70 pointer-events-none"></div>
        </div>
      </div>
    </div>
  {/if}

  <!-- Precise scrub + timestamps -->
  <div class="flex items-center gap-2 w-full max-w-md mx-auto">
    <AudioPlayerTime class="text-muted text-xs tabular-nums" />
    <AudioPlayerProgress class="flex-1" />
    <AudioPlayerDuration class="text-muted text-xs tabular-nums" />
  </div>

  <!-- Transport -->
  <div class="flex items-center justify-center gap-5">
    <button
      class="p-1.5 text-muted hover:text-primary transition-colors bg-transparent border-none cursor-pointer"
      onclick={skipBack}
      aria-label="Back 10 seconds"
    >
      <SkipBack size={18} />
    </button>
    <AudioPlayerButton variant="default" size="icon" class="size-12 rounded-full" />
    <button
      class="p-1.5 text-muted hover:text-primary transition-colors bg-transparent border-none cursor-pointer"
      onclick={skipForward}
      aria-label="Forward 10 seconds"
    >
      <SkipForward size={18} />
    </button>
  </div>

  <!-- Volume -->
  <div class="flex justify-center">
    <VolumeControl {volume} {muted} ontoggle={toggleMute} onset={setVolume} />
  </div>

  <Branding />
</div>
