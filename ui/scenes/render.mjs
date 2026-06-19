/*
  Renders the JetBrains Marketplace marketing images.

    npm run scenes

  Spins up a Vite dev server, drives the real Svelte player components headless
  via Playwright, and writes one PNG per scene to <repo>/marketing/. Self-contained:
  no separate dev server needed. Nothing here ships in the plugin build.
*/
import { createServer } from 'vite'
import { chromium } from 'playwright'
import { fileURLToPath } from 'node:url'
import { mkdirSync, realpathSync } from 'node:fs'
import path from 'node:path'

const uiRoot = fileURLToPath(new URL('..', import.meta.url)) // ui/
const repoRoot = path.resolve(uiRoot, '..')
const outDir = path.join(repoRoot, 'marketing')
mkdirSync(outDir, { recursive: true })

const PORT = 5199
const VIEWPORT = { width: 1280, height: 800 }
const SCALE = 2 // retina; PNGs come out at 2560×1600

// FFmpeg-style metadata, mirroring what the IDE probes and pushes at runtime.
const SNEAKY_SNITCH = {
  state: 'ready',
  isVideo: false,
  fileName: 'sneaky-snitch.mp3',
  fileExtension: 'MP3',
  mediaUrl: '/assets/sneaky-snitch.mp3', // served by ui/public/assets -> repo assets/
  mediaInfo: {
    codec: 'MP3', container: 'mp3', sampleRateHz: 44100, channels: 2, channelLabel: 'stereo',
    bitrateBps: 320000, durationMs: 136620, sizeBytes: 5507858,
    tags: [{ label: 'Title', value: 'Sneaky Snitch' }, { label: 'Artist', value: 'Kevin MacLeod' }],
  },
}

// One entry per marketplace image. `prepare` makes the still look right before capture.
const SCENES = [
  { name: 'hero', config: SNEAKY_SNITCH, prepare: prepareAudio },
  { name: 'audio-window', config: SNEAKY_SNITCH, prepare: prepareAudio },
]

// Wait for the in-browser-decoded waveform, then park the playhead mid-track so
// the right-pinned scroll fills the box (it's empty at t=0).
async function prepareAudio(page, config) {
  await page.waitForFunction(
    () => {
      const el = document.querySelector('[data-bars]')
      return !!el && Number(el.getAttribute('data-bars')) > 0
    },
    { timeout: 40000 },
  )
  // Fallback only if <audio> never reports a duration; derive it from the scene's
  // own metadata so this stays correct for any track, not just this one.
  const fallbackSeconds = (config.mediaInfo?.durationMs ?? 0) / 1000
  await page.evaluate(async (fallback) => {
    const a = document.querySelector('audio')
    if (!a) return
    if (!isFinite(a.duration) || a.duration <= 0) {
      await new Promise((r) => {
        a.addEventListener('loadedmetadata', r, { once: true })
        setTimeout(r, 3000)
      })
    }
    a.muted = true
    const seconds = isFinite(a.duration) && a.duration > 0 ? a.duration : fallback
    a.currentTime = seconds * 0.42
  }, fallbackSeconds)
  await page.waitForTimeout(1000)
}

// Allow serving deps even when node_modules is a symlink (git worktrees, monorepos).
const fsAllow = [uiRoot, repoRoot]
try {
  fsAllow.push(realpathSync(path.join(uiRoot, 'node_modules')))
} catch {
  /* node_modules not a symlink — uiRoot already covers it */
}

const server = await createServer({
  root: uiRoot,
  configFile: path.join(uiRoot, 'vite.config.ts'),
  server: { port: PORT, fs: { allow: fsAllow } },
  logLevel: 'warn',
})
await server.listen()
const base = (server.resolvedUrls?.local?.[0] ?? `http://localhost:${PORT}/`).replace(/\/$/, '')

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: VIEWPORT, deviceScaleFactor: SCALE, colorScheme: 'dark' })

let rendered = 0
try {
  for (const scene of SCENES) {
    const page = await ctx.newPage()
    await page.addInitScript((cfg) => {
      window.jetplay = cfg
    }, scene.config)
    await page.goto(`${base}/scenes.html?scene=${scene.name}`, { waitUntil: 'networkidle' })
    await scene.prepare(page, scene.config)
    await page.evaluate(() => document.fonts.ready)
    const out = path.join(outDir, `${scene.name}.png`)
    await page.screenshot({ path: out })
    await page.close()
    console.log(`  ✓ marketing/${scene.name}.png`)
    rendered++
  }
} finally {
  await browser.close()
  await server.close()
}

console.log(`\nrendered ${rendered} scene(s) → marketing/`)
