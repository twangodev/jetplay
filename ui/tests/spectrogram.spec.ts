import { test, expect } from './fixtures'

// 4x4 matrix of magnitude bytes, base64-encoded the way PlayerBridge ships it.
const sampleMatrix = Buffer.from(
  Array.from({ length: 16 }, (_, i) => (i * 17) % 256),
).toString('base64')

const readyAudio = {
  state: 'ready' as const,
  fileName: 'track.mp3',
  fileExtension: 'mp3',
  mediaUrl: '/assets/sintel.mp3',
  isVideo: false,
  // Seed bars so the visualization lane (and its Waveform/Spectrogram toggle) renders deterministically.
  waveform: Array.from({ length: 40 }, () => 0.5),
}

test('spectrogram toggle reveals the heatmap when data is already present', async ({ page }) => {
  await page.addInitScript(
    ([cfg, data]) => {
      ;(window as any).jetplay = cfg
      ;(window as any).__jetplaySpectrogram = {
        ok: true,
        timeCols: 4,
        freqBins: 4,
        durationMs: 1000,
        sampleRateHz: 44100,
        minHz: 20,
        maxHz: 20000,
        dbFloor: -80,
        dbCeil: 0,
        logFreq: true,
        data,
      }
    },
    [readyAudio, sampleMatrix] as const,
  )
  await page.goto('/')

  await page.getByRole('button', { name: 'Spectrogram', exact: true }).click()
  await expect(page.locator('[data-slot="spectrogram"]')).toBeVisible()
})

test('revealing the spectrogram lazily requests it when no data is present', async ({ page }) => {
  await page.addInitScript((cfg) => {
    ;(window as any).jetplay = cfg
    ;(window as any).__spectrogramRequested = false
    ;(window as any).jetplayRequestSpectrogram = () => {
      ;(window as any).__spectrogramRequested = true
    }
  }, readyAudio)
  await page.goto('/')

  await page.getByRole('button', { name: 'Spectrogram', exact: true }).click()

  await expect(page.getByText(/analyz/i)).toBeVisible()
  expect(await page.evaluate(() => (window as any).__spectrogramRequested)).toBe(true)
})

test('an unavailable spectrogram shows a message instead of a heatmap', async ({ page }) => {
  await page.addInitScript((cfg) => {
    ;(window as any).jetplay = cfg
    ;(window as any).__jetplaySpectrogram = { ok: false }
  }, readyAudio)
  await page.goto('/')

  await page.getByRole('button', { name: 'Spectrogram', exact: true }).click()
  await expect(page.getByText(/unavailable/i)).toBeVisible()
  await expect(page.locator('[data-slot="spectrogram"]')).toHaveCount(0)
})
