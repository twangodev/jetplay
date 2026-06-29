/** Resolve a CSS custom property (e.g. `--primary`) to an `rgb(...)` string via a throwaway element. */
export function cssColor(varName: string, fallback: string): string {
  if (typeof document === 'undefined') return fallback
  const el = document.createElement('span')
  el.style.cssText = 'position:absolute;opacity:0;pointer-events:none'
  el.style.color = `var(${varName})`
  document.body.appendChild(el)
  const resolved = getComputedStyle(el).color
  document.body.removeChild(el)
  return resolved || fallback
}

/** Parse `#rgb`, `#rrggbb`, `rgb(...)`, or `rgba(...)` into `[r, g, b]`; falls back to a light gray. */
export function parseRgb(color: string): [number, number, number] {
  const c = color.trim()
  if (c.startsWith('#')) {
    const hex = c.slice(1)
    const full = hex.length === 3 ? hex.split('').map((x) => x + x).join('') : hex
    const n = parseInt(full, 16)
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
  }
  const m = c.match(/(\d+(?:\.\d+)?)[,\s]+(\d+(?:\.\d+)?)[,\s]+(\d+(?:\.\d+)?)/)
  if (m) return [Math.round(+m[1]), Math.round(+m[2]), Math.round(+m[3])]
  return [229, 229, 229]
}
