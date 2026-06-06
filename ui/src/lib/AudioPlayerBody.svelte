<script lang="ts">
  import { onMount, untrack } from 'svelte'
  import { slide } from 'svelte/transition'
  import { SkipBack, SkipForward, Volume, Volume1, Volume2, VolumeX } from '@lucide/svelte'
  import {
    AudioGraph,
    AudioPlayerButton,
    AudioPlayerDuration,
    AudioPlayerProgress,
    AudioPlayerSpeed,
    AudioPlayerTime,
    precomputeWaveform,
    useAudioPlayer,
  } from '$lib/components/ui/audio-player/index.js'
  import { Button } from '$lib/components/ui/button/index.js'
  import { Waveform } from '$lib/components/ui/waveform/index.js'
  import { cn } from '$lib/utils.js'
  import Branding from './Branding.svelte'
  import { useScratchableWaveform } from './use-scratchable-waveform.svelte.js'

  let {
    src,
    fileName,
    extension,
    waveform = [],
  }: { src: string; fileName: string; extension: string; waveform?: number[] } = $props()

  const player = useAudioPlayer<{ name: string }>()
  // Owns the scratch AudioContext used by the scrubbing interaction.
  const graph = new AudioGraph()

  const BARS_PER_SECOND = 8
  const BAR_STEP = 5 // px of scroll travel per waveform bar
  // Matches WaveformExtractor.MAX_DURATION_SECONDS (the IDE-side cap).
  const WAVEFORM_MAX_SECONDS = 30 * 60

  // The IDE pushes FFmpeg-decoded bars via `waveform`; `decodedWaveform` is the
  // in-browser fallback used only when no bars were provided (HTTP / tests).
  let decodedWaveform = $state<number[]>([])
  const precomputedWaveform = $derived(waveform.length > 0 ? waveform : decodedWaveform)
  let waveformContainerEl = $state<HTMLDivElement | null>(null)
  let containerEl: HTMLDivElement | null = $state(null)
  let containerWidth = $state(300)
  const totalWidth = $derived(precomputedWaveform.length * BAR_STEP)
  // Short waveforms stretch to fill the box; longer ones scroll past the playhead.
  const displayWidth = $derived(Math.max(totalWidth, containerWidth))
  const hasWaveform = $derived(precomputedWaveform.length > 0)

  let volume = $state(1)
  let muted = $state(false)
  const VolumeIcon = $derived(
    muted || volume === 0 ? VolumeX : volume <= 0.33 ? Volume : volume <= 0.66 ? Volume1 : Volume2,
  )

  // sv11 colors the bars a muted gray rather than full --foreground. jetplay
  // tracks the IDE theme via prefers-color-scheme (dark unless prefers-light).
  let isDark = $state(true)
  const barColor = $derived(isDark ? '#a1a1aa' : '#71717a')

  // speaker-01's scratchable-waveform: drag to scrub, momentum, scratch, keyboard.
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
        /* decode failed — bars come from the IDE instead */
      })
  })

  // Track the scroll container's width. Re-runs when the waveform box mounts.
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

  // Park the playhead at the start (= right edge) once bars + width are known.
  let offsetInitialized = false
  $effect(() => {
    if (!hasWaveform || containerWidth <= 0 || offsetInitialized) return
    offsetInitialized = true
    untrack(() => (scrub.offset = containerWidth))
  })

  // Drive the scroll offset from playback position, unless the user is scrubbing.
  // Right-pinned (sv11): offset = containerWidth at start, scrolls left as it plays.
  $effect(() => {
    let id: number
    const update = () => {
      if (!scrub.isScrubbing && !scrub.isMomentumActive) {
        const a = player.audio
        if (a && !isNaN(a.duration) && a.duration > 0) {
          scrub.offset = containerWidth - (a.currentTime / a.duration) * displayWidth
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

  onMount(() => {
    containerEl?.focus()
    const mql = window.matchMedia('(prefers-color-scheme: light)')
    const sync = () => (isDark = !mql.matches)
    sync()
    mql.addEventListener('change', sync)
    return () => mql.removeEventListener('change', sync)
  })

  function toggleMute() {
    muted = !muted
  }
  function setVolume(v: number) {
    volume = v
    if (v > 0) muted = false
  }
  function handleVolumeClick(e: MouseEvent) {
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
    setVolume(Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width)))
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
      if (player.audio?.paused) void player.play()
      else void player.pause()
    } else if ((e.code === 'ArrowLeft' || e.code === 'ArrowRight') && e.target === containerEl) {
      e.preventDefault()
      if (e.code === 'ArrowLeft') skipBack()
      else skipForward()
    }
  }
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
  bind:this={containerEl}
  class="flex-1 flex items-center justify-center p-6 select-none outline-none"
  onkeydown={handleKeydown}
  tabindex="-1"
>
  <div class="relative w-full max-w-md space-y-4 rounded-xl border bg-card p-4 text-card-foreground shadow-sm">
    <!-- Metadata -->
    <div class="flex items-center gap-2">
      <span class="truncate text-sm font-medium text-foreground">{fileName}</span>
      {#if extension}
        <span class="shrink-0 rounded-sm border border-border px-1.5 py-0.5 text-[11px] font-medium tracking-wide text-muted-foreground uppercase">
          {extension}
        </span>
      {/if}
    </div>

    <!-- Scrolling / scratchable waveform (drag to scrub). Mounts once bars exist. -->
    {#if hasWaveform}
      <div transition:slide={{ duration: 450 }}>
        <!-- svelte-ignore a11y_no_static_element_interactions -->
        <div
          bind:this={waveformContainerEl}
          class="relative h-12 cursor-grab touch-none overflow-hidden rounded-lg bg-foreground/10 p-2 outline-none select-none active:cursor-grabbing dark:bg-black/80"
          role="slider"
          tabindex="0"
          aria-label="Seek playback"
          data-bars={precomputedWaveform.length}
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
              <Waveform data={precomputedWaveform} height={32} barWidth={3} barGap={2} barRadius={1} {barColor} fadeEdges={true} fadeWidth={24} />
            </div>
          </div>
        </div>
      </div>
    {/if}

    <!-- Time / scrub / duration / speed -->
    <div class="flex items-center gap-2">
      <AudioPlayerTime class="text-xs text-muted-foreground tabular-nums" />
      <AudioPlayerProgress class="flex-1" />
      <AudioPlayerDuration class="text-xs text-muted-foreground tabular-nums" />
      <AudioPlayerSpeed variant="ghost" size="icon" class="size-8 text-muted-foreground hover:text-foreground" />
    </div>

    <!-- Transport -->
    <div class="flex items-center justify-center gap-3">
      <Button
        variant="outline"
        size="icon"
        class="h-10 w-10 rounded-full border-border bg-background hover:bg-muted"
        onclick={skipBack}
        aria-label="Back 10 seconds"
      >
        <SkipBack class="size-4 text-muted-foreground" />
      </Button>
      <AudioPlayerButton
        variant="outline"
        size="icon"
        class={cn(
          'h-14 w-14 rounded-full border-border transition-all',
          player.isPlaying ? 'border-foreground/30 bg-foreground/10 hover:bg-foreground/15' : 'bg-background hover:bg-muted',
        )}
      />
      <Button
        variant="outline"
        size="icon"
        class="h-10 w-10 rounded-full border-border bg-background hover:bg-muted"
        onclick={skipForward}
        aria-label="Forward 10 seconds"
      >
        <SkipForward class="size-4 text-muted-foreground" />
      </Button>
    </div>

    <!-- Volume (always-visible, sv11-style) -->
    <div class="flex items-center justify-center gap-3 pt-1">
      <button
        type="button"
        onclick={toggleMute}
        class="text-muted-foreground transition-colors hover:text-foreground"
        aria-label="Toggle mute"
      >
        <VolumeIcon class="size-4" />
      </button>
      <!-- svelte-ignore a11y_click_events_have_key_events -->
      <!-- svelte-ignore a11y_no_static_element_interactions -->
      <div
        class="relative h-1 w-40 cursor-pointer rounded-full bg-foreground/10"
        role="slider"
        tabindex="0"
        aria-label="Volume"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round((muted ? 0 : volume) * 100)}
        onclick={handleVolumeClick}
      >
        <div class="absolute top-0 left-0 h-full rounded-full bg-primary" style:width="{(muted ? 0 : volume) * 100}%"></div>
      </div>
      <span class="w-10 text-right font-mono text-xs text-muted-foreground">{Math.round((muted ? 0 : volume) * 100)}%</span>
    </div>
  </div>

  <Branding />
</div>
