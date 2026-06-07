<script lang="ts">
  import { onMount, untrack } from 'svelte'
  import { fade, slide } from 'svelte/transition'
  import { Slider as SliderPrimitive } from 'bits-ui'
  import { ChevronDown, SkipBack, SkipForward, Volume, Volume1, Volume2, VolumeX } from '@lucide/svelte'
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
  import { formatTime } from './formatTime'
  import { formatBitrate, formatBytes, formatSampleRate } from './mediaInfoFormat'
  import { useScratchableWaveform } from './use-scratchable-waveform.svelte.js'

  let {
    src,
    fileName,
    extension,
    waveform = [],
    mediaInfo,
  }: {
    src: string
    fileName: string
    extension: string
    waveform?: number[]
    mediaInfo?: MediaInfo
  } = $props()

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

  // --- Codec inspector: the metadata header expands into a technical readout ---
  // when the IDE has pushed `mediaInfo` (FFmpeg-probed). Collapsed shows a
  // glanceable summary line; expanded shows the full label/value grid.
  let infoExpanded = $state(false)

  type InfoRow = { label: string; value: string }
  const infoRows = $derived.by<InfoRow[]>(() => {
    const m = mediaInfo
    if (!m) return []
    const rows: InfoRow[] = []
    if (m.codec) rows.push({ label: 'Codec', value: m.codec })
    if (m.container) rows.push({ label: 'Container', value: m.container.toUpperCase() })
    if (m.sampleRateHz) rows.push({ label: 'Sample rate', value: formatSampleRate(m.sampleRateHz) })
    if (m.channels)
      rows.push({ label: 'Channels', value: m.channelLabel ? `${m.channels} (${m.channelLabel})` : String(m.channels) })
    if (m.bitDepth) rows.push({ label: 'Bit depth', value: m.bitDepth })
    if (m.bitrateBps) rows.push({ label: 'Bitrate', value: formatBitrate(m.bitrateBps) })
    if (m.durationMs) rows.push({ label: 'Duration', value: formatTime(m.durationMs / 1000) })
    if (m.sizeBytes) rows.push({ label: 'Size', value: formatBytes(m.sizeBytes) })
    return rows
  })

  const summaryLine = $derived.by(() => {
    const m = mediaInfo
    if (!m) return ''
    // Container is intentionally omitted — the extension badge already conveys
    // the format. The exact demux container still shows in the expanded grid.
    const parts: string[] = []
    if (m.sampleRateHz) parts.push(formatSampleRate(m.sampleRateHz))
    if (m.bitDepth) parts.push(m.bitDepth)
    if (m.channelLabel) parts.push(m.channelLabel)
    if (m.sizeBytes) parts.push(formatBytes(m.sizeBytes))
    return parts.join(' · ')
  })

  // Embedded text tags (title/artist/album/…) and cover art the IDE probed.
  const tags = $derived(mediaInfo?.tags ?? [])
  const albumArt = $derived(mediaInfo?.albumArt)

  // Only treat info as present when there's something to expand into, so an
  // empty/degenerate push never renders a chevron with nothing behind it.
  const hasMediaInfo = $derived(infoRows.length > 0 || tags.length > 0)

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
  function skipBack() {
    const a = player.audio
    if (a) a.currentTime = Math.max(0, a.currentTime - 10)
  }
  function skipForward() {
    const a = player.audio
    if (a && isFinite(a.duration)) a.currentTime = Math.min(a.duration, a.currentTime + 10)
  }
  function handleKeydown(e: KeyboardEvent) {
    // Only the player container itself drives these shortcuts. When an inner
    // control (the media-details toggle, mute, the volume slider) is focused,
    // let it receive the key natively — e.g. Space must activate the toggle,
    // not get swallowed here into a play/pause.
    if (e.target !== containerEl) return
    if (e.code === 'Space') {
      e.preventDefault()
      if (player.audio?.paused) void player.play()
      else void player.pause()
    } else if (e.code === 'ArrowLeft' || e.code === 'ArrowRight') {
      e.preventDefault()
      if (e.code === 'ArrowLeft') skipBack()
      else skipForward()
    }
  }
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
  bind:this={containerEl}
  class="relative flex-1 flex items-center justify-center overflow-hidden p-6 select-none outline-none"
  onkeydown={handleKeydown}
  tabindex="-1"
>
  {#if albumArt}
    <!-- Ambient blurred cover behind the card — no thumbnail, just the wash of color. -->
    <div
      class="pointer-events-none absolute inset-0 z-0 overflow-hidden"
      aria-hidden="true"
      data-slot="album-art"
      transition:fade={{ duration: 400 }}
    >
      <img src={albumArt} alt="" class="h-full w-full scale-125 object-cover opacity-40 blur-3xl saturate-150" />
      <div class="absolute inset-0 bg-background/50"></div>
    </div>
  {/if}
  <div class="relative z-10 w-full max-w-md space-y-4 rounded-xl border bg-card p-4 text-card-foreground shadow-sm">
    <!-- Metadata header — expands into the codec inspector when info is available -->
    <div class="space-y-1.5">
      {#snippet nameAndBadge()}
        <span class="truncate text-sm font-medium text-foreground">{fileName}</span>
        {#if extension}
          <span class="shrink-0 rounded-sm border border-border px-1.5 py-0.5 text-[11px] font-medium tracking-wide text-muted-foreground uppercase">
            {extension}
          </span>
        {/if}
      {/snippet}

      {#if hasMediaInfo}
        <button
          type="button"
          class="flex w-full items-center gap-2 text-left outline-none focus-visible:ring-2 focus-visible:ring-ring rounded-sm"
          aria-expanded={infoExpanded}
          aria-controls="media-info-panel"
          aria-label="Toggle media details"
          onclick={() => (infoExpanded = !infoExpanded)}
        >
          {@render nameAndBadge()}
          <ChevronDown
            class={cn(
              'ml-auto size-4 shrink-0 text-muted-foreground transition-transform',
              infoExpanded && 'rotate-180',
            )}
          />
        </button>
      {:else}
        <div class="flex items-center gap-2">
          {@render nameAndBadge()}
        </div>
      {/if}

      {#if summaryLine}
        <div
          data-slot="media-info-summary"
          transition:slide={{ duration: 300 }}
          class="truncate text-xs text-muted-foreground tabular-nums"
        >
          {summaryLine}
        </div>
      {/if}

      {#if hasMediaInfo && infoExpanded}
        <div id="media-info-panel" transition:slide={{ duration: 250 }} class="space-y-3">
          {#if tags.length > 0}
            <!-- Descriptive embedded tags (what is this) -->
            <div
              data-slot="media-info-tags"
              class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 rounded-lg bg-foreground/5 p-3 text-xs"
            >
              {#each tags as tag (tag.label)}
                <span class="text-muted-foreground">{tag.label}</span>
                <span class="break-words text-foreground">{tag.value}</span>
              {/each}
            </div>
          {/if}
          {#if infoRows.length > 0}
            <!-- Technical stream details (how it's encoded) -->
            <div
              data-slot="media-info-grid"
              class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 rounded-lg bg-foreground/5 p-3 text-xs"
            >
              {#each infoRows as row (row.label)}
                <span class="text-muted-foreground">{row.label}</span>
                <span class="font-mono text-foreground">{row.value}</span>
              {/each}
            </div>
          {/if}
        </div>
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
      <!-- Same bits-ui Slider the scrub bar (AudioPlayerProgress) uses, styled to match. -->
      <SliderPrimitive.Root
        type="single"
        value={muted ? 0 : volume}
        min={0}
        max={1}
        step={0.01}
        aria-label="Volume"
        onValueChange={(v) => setVolume(v)}
        class="group/vol relative flex h-4 w-40 touch-none items-center select-none"
      >
        {#snippet children({ thumbItems })}
          <span class="bg-muted relative h-[4px] w-full grow overflow-hidden rounded-full">
            <SliderPrimitive.Range class="bg-primary absolute h-full" />
          </span>
          {#each thumbItems as thumb (thumb.index)}
            <SliderPrimitive.Thumb
              index={thumb.index}
              class="relative flex h-0 w-0 items-center justify-center opacity-0 group-hover/vol:opacity-100 focus-visible:opacity-100 focus-visible:outline-none"
            >
              <div class="bg-foreground absolute size-3 rounded-full"></div>
            </SliderPrimitive.Thumb>
          {/each}
        {/snippet}
      </SliderPrimitive.Root>
      <span class="w-10 text-right font-mono text-xs text-muted-foreground">{Math.round((muted ? 0 : volume) * 100)}%</span>
    </div>
  </div>

  <Branding />
</div>
