// The spectrogram is tinted with the player's own foreground colour (like the waveform bars) rather
// than a rainbow heatmap, so it reads as part of the same UI. Magnitude drives opacity; the hottest
// cells brighten toward white (dark theme) or black (light theme) so peaks still stand out.

/** Parse `#rgb`, `#rrggbb`, or `rgb(r,g,b)` into `[r, g, b]`; falls back to zinc-400. */
export function parseRgb(color: string): [number, number, number] {
  const c = color.trim()
  if (c.startsWith('#')) {
    const hex = c.slice(1)
    const full = hex.length === 3 ? hex.split('').map((x) => x + x).join('') : hex
    const n = parseInt(full, 16)
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
  }
  const m = c.match(/(\d+)[,\s]+(\d+)[,\s]+(\d+)/)
  if (m) return [Number(m[1]), Number(m[2]), Number(m[3])]
  return [161, 161, 170]
}

const lerp = (a: number, b: number, t: number) => a + (b - a) * t

const smoothstep = (edge0: number, edge1: number, x: number) => {
  const t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)))
  return t * t * (3 - 2 * t)
}

const clamp01 = (x: number) => Math.max(0, Math.min(1, x))

const NOISE_FLOOR = 0.1 // magnitudes below this fade to fully transparent for clean blacks
const ALPHA_GAMMA = 0.85
const DIM_BASE = 0.45 // how dark the colour is at low energy (vs full foreground)
const HOT_START = 0.62 // where peaks start blooming toward white/black
const HOT_STRENGTH = 0.95

/** 256-entry RGBA lookup table indexed by magnitude byte; `lut[i*4 + {0,1,2,3}]`. */
export function intensityLut(foreground: string, isDark: boolean): Uint8Array {
  const [r, g, b] = parseRgb(foreground)
  const peak = isDark ? 255 : 8
  const lut = new Uint8Array(256 * 4)
  for (let i = 0; i < 256; i++) {
    const t = i / 255
    const alpha = Math.pow(clamp01((t - NOISE_FLOOR) / (1 - NOISE_FLOOR)), ALPHA_GAMMA)
    const lum = DIM_BASE + (1 - DIM_BASE) * Math.pow(t, 0.75) // dim at low energy, full colour by mid
    const hot = smoothstep(HOT_START, 1, t) * HOT_STRENGTH // bloom toward the peak colour at the top
    lut[i * 4] = Math.round(lerp(r * lum, peak, hot))
    lut[i * 4 + 1] = Math.round(lerp(g * lum, peak, hot))
    lut[i * 4 + 2] = Math.round(lerp(b * lum, peak, hot))
    lut[i * 4 + 3] = Math.round(alpha * 255)
  }
  return lut
}
