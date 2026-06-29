<script lang="ts">
  import { useAudioPlayer } from '$lib/components/ui/audio-player/index.js'
  import { cn } from '$lib/utils.js'
  import { magmaLut } from './colormap.js'

  let {
    payload,
    height = 160,
    class: className,
  }: {
    payload: SpectrogramData
    height?: number
    class?: string
  } = $props()

  const player = useAudioPlayer()
  const lut = magmaLut()

  let containerEl: HTMLDivElement | null = $state(null)
  let canvasEl: HTMLCanvasElement | null = $state(null)
  let isDragging = $state(false)
  let playheadPct = $state(0)

  // Decode the base64 matrix once per payload and bake it into an offscreen RGBA bitmap
  // (one source pixel per cell); the visible canvas just scales this to its display size.
  const source = $derived.by(() => {
    const cols = payload.timeCols ?? 0
    const bins = payload.freqBins ?? 0
    if (!payload.data || cols <= 0 || bins <= 0) return null
    const bytes = base64ToBytes(payload.data)
    if (bytes.length < cols * bins) return null
    const off = document.createElement('canvas')
    off.width = cols
    off.height = bins
    const octx = off.getContext('2d')
    if (!octx) return null
    const img = octx.createImageData(cols, bins)
    for (let col = 0; col < cols; col++) {
      for (let y = 0; y < bins; y++) {
        const bin = bins - 1 - y // high frequencies on top
        const v = bytes[col * bins + bin]
        const px = (y * cols + col) * 4
        img.data[px] = lut[v * 3]
        img.data[px + 1] = lut[v * 3 + 1]
        img.data[px + 2] = lut[v * 3 + 2]
        img.data[px + 3] = 255
      }
    }
    octx.putImageData(img, 0, 0)
    return off
  })

  $effect(() => {
    const canvas = canvasEl
    const container = containerEl
    const src = source
    if (!canvas || !container) return

    const render = () => {
      const ctx = canvas.getContext('2d')
      if (!ctx) return
      const rect = canvas.getBoundingClientRect()
      ctx.clearRect(0, 0, rect.width, rect.height)
      if (src && src.width > 0 && src.height > 0) {
        ctx.imageSmoothingEnabled = true
        ctx.drawImage(src, 0, 0, src.width, src.height, 0, 0, rect.width, rect.height)
      }
    }

    const resizeObserver = new ResizeObserver(() => {
      const rect = container.getBoundingClientRect()
      const dpr = window.devicePixelRatio || 1
      canvas.width = rect.width * dpr
      canvas.height = rect.height * dpr
      canvas.style.width = `${rect.width}px`
      canvas.style.height = `${rect.height}px`
      const ctx = canvas.getContext('2d')
      if (ctx) {
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
        render()
      }
    })
    resizeObserver.observe(container)
    render()
    return () => resizeObserver.disconnect()
  })

  // Moving playhead, rAF-driven from the shared player clock (fixed view, not scrolling).
  $effect(() => {
    let id: number
    const tick = () => {
      const a = player.audio
      if (a && !isDragging && isFinite(a.duration) && a.duration > 0) {
        playheadPct = (a.currentTime / a.duration) * 100
      }
      id = requestAnimationFrame(tick)
    }
    id = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(id)
  })

  function seekToClientX(clientX: number) {
    const a = player.audio
    if (!a || !containerEl || !isFinite(a.duration) || a.duration <= 0) return
    const rect = containerEl.getBoundingClientRect()
    const x = Math.max(0, Math.min(clientX - rect.left, rect.width))
    const pct = rect.width > 0 ? x / rect.width : 0
    playheadPct = pct * 100
    a.currentTime = pct * a.duration
  }

  function handlePointerDown(event: PointerEvent) {
    event.preventDefault()
    isDragging = true
    seekToClientX(event.clientX)
    const handleMove = (moveEvent: PointerEvent) => seekToClientX(moveEvent.clientX)
    const handleUp = () => {
      isDragging = false
      window.removeEventListener('pointermove', handleMove)
      window.removeEventListener('pointerup', handleUp)
    }
    window.addEventListener('pointermove', handleMove)
    window.addEventListener('pointerup', handleUp, { once: true })
  }

  function base64ToBytes(b64: string): Uint8Array {
    const binary = atob(b64)
    const out = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i)
    return out
  }
</script>

<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
  bind:this={containerEl}
  data-slot="spectrogram"
  role="slider"
  tabindex={0}
  aria-label="Spectrogram seek"
  aria-valuemin={0}
  aria-valuemax={100}
  aria-valuenow={Math.round(playheadPct)}
  class={cn('relative cursor-pointer touch-none overflow-hidden select-none', className)}
  style:height="{height}px"
  onpointerdown={handlePointerDown}
>
  <canvas bind:this={canvasEl} class="block h-full w-full"></canvas>
  <div class="bg-primary pointer-events-none absolute top-0 bottom-0 w-px" style:left="{playheadPct}%"></div>
</div>
