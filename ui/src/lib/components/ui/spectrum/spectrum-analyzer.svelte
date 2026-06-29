<script lang="ts">
  import { useAudioPlayer } from '$lib/components/ui/audio-player/index.js'
  import { cn } from '$lib/utils.js'
  import { cssColor, parseRgb } from './colors.js'

  // Real-time FFT spectrum analyzer (à la FabFilter Pro-Q / Voxengo SPAN): log-frequency X, dB Y,
  // animated from the precomputed STFT matrix — the column under the playhead is the current frame.
  let {
    payload,
    isDark = true,
    class: className,
  }: {
    payload: SpectrogramData
    isDark?: boolean
    class?: string
  } = $props()

  const player = useAudioPlayer()

  let containerEl: HTMLDivElement | null = $state(null)
  let canvasEl: HTMLCanvasElement | null = $state(null)

  const FREQ_TICKS: [number, string][] = [
    [100, '100'],
    [1000, '1k'],
    [10000, '10k'],
  ]
  const DB_TICKS = [-20, -40, -60]
  const RELEASE = 0.3 // how fast the curve falls (1 = instant); rises are instant
  const PEAK_DECAY = 0.004 // peak-hold fall per frame, in normalized units
  const PAD_TOP = 6
  const PAD_BOTTOM = 15

  const matrix = $derived.by(() => {
    const cols = payload.timeCols ?? 0
    const bins = payload.freqBins ?? 0
    if (!payload.data || cols <= 0 || bins <= 0) return null
    const bytes = base64ToBytes(payload.data)
    if (bytes.length < cols * bins) return null
    return { bytes, cols, bins }
  })

  $effect(() => {
    const canvas = canvasEl
    const container = containerEl
    const m = matrix
    if (!canvas || !container || !m) return
    void isDark // re-init colours when the theme flips

    const minHz = payload.minHz ?? 20
    const maxHz = payload.maxHz ?? 20000
    const dbFloor = payload.dbFloor ?? -80
    const dbCeil = payload.dbCeil ?? 0
    const logRange = Math.log(maxHz / minHz)

    const [lr, lg, lb] = parseRgb(cssColor('--primary', isDark ? '#e5e5e5' : '#18181b'))
    const [gr, gg, gb] = parseRgb(cssColor('--muted-foreground', '#71717a'))
    const accent = (a: number) => `rgba(${lr},${lg},${lb},${a})`
    const grid = (a: number) => `rgba(${gr},${gg},${gb},${a})`

    const disp = new Float32Array(m.bins)
    const smooth = new Float32Array(m.bins)
    const peak = new Float32Array(m.bins)
    let cssW = 0
    let cssH = 0
    let raf = 0

    const freqToX = (hz: number) => (Math.log(hz / minHz) / logRange) * cssW

    const draw = () => {
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        raf = requestAnimationFrame(draw)
        return
      }

      const audio = player.audio
      const dur = audio && isFinite(audio.duration) && audio.duration > 0 ? audio.duration : (payload.durationMs ?? 0) / 1000
      const p = dur > 0 ? (audio ? audio.currentTime : 0) / dur : 0
      const col = Math.min(m.cols - 1, Math.max(0, Math.floor(p * m.cols)))

      for (let b = 0; b < m.bins; b++) {
        const target = m.bytes[col * m.bins + b] / 255
        disp[b] = target > disp[b] ? target : disp[b] + (target - disp[b]) * RELEASE
        peak[b] = Math.max(disp[b], peak[b] - PEAK_DECAY)
      }
      // Light 1-2-1 smoothing for a cleaner analyzer curve.
      for (let b = 0; b < m.bins; b++) {
        const l = b > 0 ? disp[b - 1] : disp[b]
        const r = b < m.bins - 1 ? disp[b + 1] : disp[b]
        smooth[b] = (l + 2 * disp[b] + r) / 4
      }

      const plotH = Math.max(1, cssH - PAD_TOP - PAD_BOTTOM)
      const yOf = (norm: number) => PAD_TOP + (1 - norm) * plotH
      const xOf = (bin: number) => (bin / (m.bins - 1)) * cssW

      ctx.clearRect(0, 0, cssW, cssH)

      // dB gridlines + labels
      ctx.lineWidth = 1
      ctx.font = '10px ui-sans-serif, system-ui, sans-serif'
      ctx.textBaseline = 'middle'
      for (const db of DB_TICKS) {
        const y = yOf((db - dbFloor) / (dbCeil - dbFloor))
        ctx.strokeStyle = grid(0.1)
        ctx.beginPath()
        ctx.moveTo(0, y)
        ctx.lineTo(cssW, y)
        ctx.stroke()
        ctx.fillStyle = grid(0.5)
        ctx.fillText(`${db}`, 3, y)
      }

      // frequency gridlines + labels
      ctx.textBaseline = 'alphabetic'
      for (const [hz, label] of FREQ_TICKS) {
        if (hz <= minHz || hz >= maxHz) continue
        const x = freqToX(hz)
        ctx.strokeStyle = grid(0.1)
        ctx.beginPath()
        ctx.moveTo(x, PAD_TOP)
        ctx.lineTo(x, cssH - PAD_BOTTOM)
        ctx.stroke()
        ctx.fillStyle = grid(0.5)
        ctx.fillText(label, x + 3, cssH - 4)
      }

      // filled spectrum
      ctx.beginPath()
      for (let b = 0; b < m.bins; b++) {
        const x = xOf(b)
        const y = yOf(smooth[b])
        b === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)
      }
      const grad = ctx.createLinearGradient(0, PAD_TOP, 0, cssH - PAD_BOTTOM)
      grad.addColorStop(0, accent(0.4))
      grad.addColorStop(1, accent(0.03))
      ctx.lineTo(cssW, cssH - PAD_BOTTOM)
      ctx.lineTo(0, cssH - PAD_BOTTOM)
      ctx.closePath()
      ctx.fillStyle = grad
      ctx.fill()

      // peak-hold line
      ctx.beginPath()
      for (let b = 0; b < m.bins; b++) {
        const x = xOf(b)
        const y = yOf(peak[b])
        b === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)
      }
      ctx.strokeStyle = accent(0.3)
      ctx.lineWidth = 1
      ctx.stroke()

      // live spectrum line
      ctx.beginPath()
      for (let b = 0; b < m.bins; b++) {
        const x = xOf(b)
        const y = yOf(smooth[b])
        b === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)
      }
      ctx.strokeStyle = accent(0.95)
      ctx.lineWidth = 1.5
      ctx.lineJoin = 'round'
      ctx.stroke()

      raf = requestAnimationFrame(draw)
    }

    const resizeObserver = new ResizeObserver(() => {
      const rect = container.getBoundingClientRect()
      const dpr = window.devicePixelRatio || 1
      cssW = rect.width
      cssH = rect.height
      canvas.width = Math.max(1, Math.round(cssW * dpr))
      canvas.height = Math.max(1, Math.round(cssH * dpr))
      canvas.style.width = `${cssW}px`
      canvas.style.height = `${cssH}px`
      const ctx = canvas.getContext('2d')
      if (ctx) ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    })
    resizeObserver.observe(container)
    raf = requestAnimationFrame(draw)
    return () => {
      cancelAnimationFrame(raf)
      resizeObserver.disconnect()
    }
  })

  function base64ToBytes(b64: string): Uint8Array {
    const binary = atob(b64)
    const out = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i)
    return out
  }
</script>

<div bind:this={containerEl} data-slot="spectrum-analyzer" class={cn('relative', className)}>
  <canvas bind:this={canvasEl} class="block h-full w-full"></canvas>
</div>
