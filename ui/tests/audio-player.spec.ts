import { test, expect } from './fixtures'

const audioConfig = {
  state: 'ready' as const,
  fileName: 'sintel.ogg',
  fileExtension: 'ogg',
  mediaUrl: '/assets/sintel.ogg',
  isVideo: false,
}

// Regression guard: the IDE serves media as file:// into a null-origin
// loadHTML page, where `crossorigin` would fail the CORS check and the audio
// would never load. A re-pull of the sv11 component must not reintroduce it.
test('audio element has no crossorigin attribute', async ({ loadApp }) => {
  const page = await loadApp(audioConfig)
  const crossorigin = await page.locator('audio').getAttribute('crossorigin')
  expect(crossorigin).toBeNull()
})

// The IDE decodes the waveform with FFmpeg and pushes the bars (the browser
// can't read file:// bytes). Use a non-decodable URL so the browser fallback
// can't mask the bridge path.
test('waveform bars pushed from the IDE render the waveform', async ({ loadApp }) => {
  const page = await loadApp({ ...audioConfig, mediaUrl: '/assets/does-not-exist.mp3' })
  const waveform = page.locator('[aria-label="Seek playback"]')
  await expect(waveform).toHaveCount(0)

  await page.evaluate(() => window.jetplayWaveform?.(Array.from({ length: 40 }, (_, i) => (i % 5) / 5)))
  await expect(waveform).toBeVisible()
})

test('play button toggles playback', async ({ loadApp }) => {
  const page = await loadApp(audioConfig)
  const playBtn = page.locator('button.rounded-full')

  // Initially paused — Play icon visible
  await expect(playBtn).toBeVisible()
  await expect(page.locator('audio')).toHaveJSProperty('paused', true)

  await playBtn.click()
  await expect(page.locator('audio')).toHaveJSProperty('paused', false)

  await playBtn.click()
  await expect(page.locator('audio')).toHaveJSProperty('paused', true)
})

test('skip forward button advances time', async ({ loadApp }) => {
  const page = await loadApp(audioConfig)
  const audio = page.locator('audio')

  // Wait for metadata so skipForward doesn't clamp to zero duration
  await page.waitForFunction(() => {
    const el = document.querySelector('audio')
    return el && el.duration > 0
  })

  const timeBefore = await audio.evaluate((el: HTMLAudioElement) => el.currentTime)

  const skipForwardBtn = page.locator('button').filter({ has: page.locator('[class*="lucide-skip-forward"]') })
  await skipForwardBtn.click()

  const timeAfter = await audio.evaluate((el: HTMLAudioElement) => el.currentTime)
  expect(timeAfter).toBeGreaterThan(timeBefore)
})

test('skip backward button decreases time', async ({ loadApp }) => {
  const page = await loadApp(audioConfig)
  const audio = page.locator('audio')

  // Wait for metadata so seeks are effective
  await page.waitForFunction(() => {
    const el = document.querySelector('audio')
    return el && el.duration > 0
  })

  // Seek near the end, then skip back
  await audio.evaluate((el: HTMLAudioElement) => {
    el.currentTime = el.duration
  })

  const timeBefore = await audio.evaluate((el: HTMLAudioElement) => el.currentTime)

  const skipBackBtn = page.locator('button').filter({ has: page.locator('[class*="lucide-skip-back"]') })
  await skipBackBtn.click()

  const timeAfter = await audio.evaluate((el: HTMLAudioElement) => el.currentTime)
  expect(timeAfter).toBeLessThan(timeBefore)
})

test('space key toggles playback', async ({ loadApp }) => {
  const page = await loadApp(audioConfig)
  const audio = page.locator('audio')

  // Focus the player container (has tabindex="-1")
  await page.locator('[tabindex="-1"]').focus()

  await page.keyboard.press('Space')
  await expect(audio).toHaveJSProperty('paused', false)

  await page.keyboard.press('Space')
  await expect(audio).toHaveJSProperty('paused', true)
})

test('arrow keys skip forward and backward', async ({ loadApp }) => {
  const page = await loadApp(audioConfig)
  const audio = page.locator('audio')

  // Wait for media to load
  await page.waitForFunction(() => {
    const el = document.querySelector('audio')
    return el && el.duration > 0
  })

  await page.locator('[tabindex="-1"]').focus()

  // ArrowRight skips +10s from 0 (clamped to duration for short clips)
  await page.keyboard.press('ArrowRight')
  const afterRight = await audio.evaluate((el: HTMLAudioElement) => el.currentTime)
  expect(afterRight).toBeGreaterThan(0)

  // ArrowLeft skips -10s
  await page.keyboard.press('ArrowLeft')
  const afterLeft = await audio.evaluate((el: HTMLAudioElement) => el.currentTime)
  expect(afterLeft).toBeLessThan(afterRight)
})

test('seek bar click seeks to position', async ({ loadApp }) => {
  const page = await loadApp(audioConfig)
  const audio = page.locator('audio')

  // Wait for duration to load
  await page.waitForFunction(() => {
    const el = document.querySelector('audio')
    return el && el.duration > 0
  })

  const seekBar = page.locator('[data-slot="audio-player-progress"]')
  const box = await seekBar.boundingBox()
  if (!box) throw new Error('Progress bar not visible')

  // Click at ~50% of the progress bar (the waveform above is drag-to-scrub)
  await seekBar.click({ position: { x: box.width * 0.5, y: box.height / 2 } })

  const currentTime = await audio.evaluate((el: HTMLAudioElement) => el.currentTime)
  const duration = await audio.evaluate((el: HTMLAudioElement) => el.duration)
  // Should be roughly in the middle (within 20% tolerance)
  expect(currentTime / duration).toBeGreaterThan(0.3)
  expect(currentTime / duration).toBeLessThan(0.7)
})