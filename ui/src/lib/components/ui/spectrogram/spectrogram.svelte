<script lang="ts">
  import { cn } from '$lib/utils.js'
  import { intensityLut } from './colormap.js'

  // A pure renderer: it paints the heatmap to fill its box. The parent owns scrolling and seeking,
  // so this scrolls in lockstep with the waveform when placed in the same scrub container.
  let {
    payload,
    barColor = '#a1a1aa',
    isDark = true,
    class: className,
  }: {
    payload: SpectrogramData
    barColor?: string
    isDark?: boolean
    class?: string
  } = $props()

  let containerEl: HTMLDivElement | null = $state(null)
  let canvasEl: HTMLCanvasElement | null = $state(null)

  // Decode the base64 matrix and bake a one-pixel-per-cell bitmap once per payload/theme;
  // the visible canvas just scales this to its display size.
  const source = $derived.by(() => {
    const cols = payload.timeCols ?? 0
    const bins = payload.freqBins ?? 0
    if (!payload.data || cols <= 0 || bins <= 0) return null
    const bytes = base64ToBytes(payload.data)
    if (bytes.length < cols * bins) return null
    const lut = intensityLut(barColor, isDark)
    const off = document.createElement('canvas')
    off.width = cols
    off.height = bins
    const octx = off.getContext('2d')
    if (!octx) return null
    const img = octx.createImageData(cols, bins)
    for (let col = 0; col < cols; col++) {
      for (let y = 0; y < bins; y++) {
        const bin = bins - 1 - y // high frequencies on top
        const v = bytes[col * bins + bin] * 4
        const px = (y * cols + col) * 4
        img.data[px] = lut[v]
        img.data[px + 1] = lut[v + 1]
        img.data[px + 2] = lut[v + 2]
        img.data[px + 3] = lut[v + 3]
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
      if (src && src.width > 0 && rect.width > 0) {
        ctx.imageSmoothingEnabled = true
        ctx.imageSmoothingQuality = 'high'
        ctx.drawImage(src, 0, 0, src.width, src.height, 0, 0, rect.width, rect.height)
      }
    }

    const resizeObserver = new ResizeObserver(() => {
      const rect = container.getBoundingClientRect()
      const dpr = window.devicePixelRatio || 1
      canvas.width = Math.max(1, Math.round(rect.width * dpr))
      canvas.height = Math.max(1, Math.round(rect.height * dpr))
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

  function base64ToBytes(b64: string): Uint8Array {
    const binary = atob(b64)
    const out = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i)
    return out
  }
</script>

<div bind:this={containerEl} data-slot="spectrogram" class={cn('h-full w-full', className)}>
  <canvas bind:this={canvasEl} class="block h-full w-full"></canvas>
</div>
