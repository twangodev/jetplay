import { type Page } from '@playwright/test'
import { test, expect } from './fixtures'

const videoConfig = {
  state: 'ready' as const,
  fileName: 'sintel.webm',
  fileExtension: 'webm',
  mediaUrl: '/assets/sintel.webm',
  isVideo: true,
}

const waitForVideoReady = (page: Page) =>
  page.waitForFunction(() => {
    const el = document.querySelector('video')
    return el != null && el.duration > 0
  })

test('click on video toggles playback', async ({ loadApp }) => {
  const page = await loadApp(videoConfig)
  const video = page.locator('video')

  await expect(video).toHaveJSProperty('paused', true)

  await video.click()
  await expect(video).toHaveJSProperty('paused', false)

  await video.click()
  await expect(video).toHaveJSProperty('paused', true)
})

test('play/pause button in overlay controls', async ({ loadApp }) => {
  const page = await loadApp(videoConfig)
  const video = page.locator('video')

  const playBtn = page.locator('button[aria-label="Play"], button[aria-label="Pause"]')
  await expect(playBtn).toBeVisible()

  await playBtn.click()
  await expect(video).toHaveJSProperty('paused', false)

  await playBtn.click()
  await expect(video).toHaveJSProperty('paused', true)
})

test('modern transport controls are present', async ({ loadApp }) => {
  const page = await loadApp(videoConfig)
  for (const label of [
    'Back 10 seconds',
    'Previous frame',
    'Next frame',
    'Forward 10 seconds',
    'Playback speed',
  ]) {
    await expect(page.locator(`button[aria-label="${label}"]`)).toBeVisible()
  }
})

test('forward 10s button advances time', async ({ loadApp }) => {
  const page = await loadApp(videoConfig)
  const video = page.locator('video')
  await waitForVideoReady(page)
  const before = await video.evaluate((el: HTMLVideoElement) => el.currentTime)
  await page.locator('button[aria-label="Forward 10 seconds"]').click()
  const after = await video.evaluate((el: HTMLVideoElement) => el.currentTime)
  expect(after).toBeGreaterThan(before)
})

test('speed dropdown changes playback rate', async ({ loadApp }) => {
  const page = await loadApp(videoConfig)
  await page.locator('button[aria-label="Playback speed"]').click()
  await page.getByText('1.5x', { exact: true }).click()
  await expect(page.locator('video')).toHaveJSProperty('playbackRate', 1.5)
})

test('media-info toggle reveals the video metadata panel', async ({ loadApp }) => {
  const page = await loadApp(videoConfig)
  // No inspector until the IDE pushes metadata.
  await expect(page.locator('button[aria-label="Toggle media details"]')).toHaveCount(0)

  await page.evaluate(() =>
    window.jetplayMediaInfo?.({
      container: 'webm',
      durationMs: 5000,
      sizeBytes: 583000,
      width: 854,
      height: 480,
      frameRate: 24,
      videoCodec: 'vp9',
      pixelFormat: 'yuv420p',
      codec: 'opus',
      sampleRateHz: 48000,
      channels: 2,
      channelLabel: 'stereo',
    }),
  )

  const toggle = page.locator('button[aria-label="Toggle media details"]')
  await expect(toggle).toBeVisible()
  await expect(page.locator('[data-slot="media-info-panel"]')).toHaveCount(0)

  await toggle.click()
  const panel = page.locator('[data-slot="media-info-panel"]')
  await expect(panel).toBeVisible()
  await expect(panel).toContainText('Resolution')
  await expect(panel).toContainText('854×480')
  await expect(panel).toContainText('vp9')
  await expect(panel).toContainText('opus') // audio stream too
})

test('space key toggles playback', async ({ loadApp }) => {
  const page = await loadApp(videoConfig)
  const video = page.locator('video')

  await page.locator('[tabindex="-1"]').focus()

  await page.keyboard.press('Space')
  await expect(video).toHaveJSProperty('paused', false)

  await page.keyboard.press('Space')
  await expect(video).toHaveJSProperty('paused', true)
})

test('arrow keys skip forward and backward', async ({ loadApp }) => {
  const page = await loadApp(videoConfig)
  const video = page.locator('video')

  await waitForVideoReady(page)

  await page.locator('[tabindex="-1"]').focus()

  // ArrowRight skips +5s from 0
  await page.keyboard.press('ArrowRight')
  const afterRight = await video.evaluate((el: HTMLVideoElement) => el.currentTime)
  expect(afterRight).toBeGreaterThanOrEqual(4)

  // ArrowLeft skips -5s
  await page.keyboard.press('ArrowLeft')
  const afterLeft = await video.evaluate((el: HTMLVideoElement) => el.currentTime)
  expect(afterLeft).toBeLessThan(afterRight)
})

test('controls auto-hide when playing', async ({ loadApp }) => {
  const page = await loadApp(videoConfig)
  const controls = page.locator('.bg-gradient-to-t').locator('..')

  // Controls visible initially
  await expect(controls).not.toHaveClass(/opacity-0/)

  // Start playing
  await page.locator('video').click()
  await expect(page.locator('video')).toHaveJSProperty('paused', false)

  // Wait for controls to auto-hide (3s timer in component)
  await expect(controls).toHaveClass(/opacity-0/, { timeout: 5000 })
})

test('mouse movement shows controls when hidden', async ({ loadApp }) => {
  const page = await loadApp(videoConfig)
  const controls = page.locator('.bg-gradient-to-t').locator('..')

  // Start playing and wait for controls to hide
  await page.locator('video').click()
  await expect(controls).toHaveClass(/opacity-0/, { timeout: 5000 })

  // Move mouse to show controls
  await page.locator('[tabindex="-1"]').hover()
  await expect(controls).not.toHaveClass(/opacity-0/)
})

// Regression: the seek Slider's onValueChange fires on every programmatic value
// change (each timeupdate), so without an interaction guard the player re-seeks
// itself every frame and stutters. A freely-playing video must not self-seek.
test('playing does not self-seek (no stutter feedback loop)', async ({ loadApp }) => {
  const page = await loadApp(videoConfig)
  const video = page.locator('video')
  await waitForVideoReady(page)
  await page.evaluate(() => {
    const el = document.querySelector('video')!
    ;(window as unknown as { __seeks: number }).__seeks = 0
    el.addEventListener('seeking', () => {
      ;(window as unknown as { __seeks: number }).__seeks++
    })
  })
  await page.locator('button[aria-label="Play"]').click()
  await page.waitForTimeout(1200)

  const seeks = await page.evaluate(() => (window as unknown as { __seeks: number }).__seeks)
  expect(seeks).toBeLessThanOrEqual(1)
  expect(await video.evaluate((el: HTMLVideoElement) => el.currentTime)).toBeGreaterThan(0.3)
})

// Regression: bits-ui fires onValueChange in the thumb's bubble phase before a
// Root-level keydown could open the guard, so arrow nudges on a focused seek
// thumb were swallowed. A capture-phase handler must open the guard in time.
test('keyboard arrows on the focused seek thumb actually seek', async ({ loadApp }) => {
  const page = await loadApp(videoConfig)
  const video = page.locator('video')
  await waitForVideoReady(page)
  await page.locator('[data-seek-slider] [role="slider"]').focus()
  const before = await video.evaluate((el: HTMLVideoElement) => el.currentTime)
  for (let i = 0; i < 12; i++) await page.keyboard.press('ArrowRight')
  const after = await video.evaluate((el: HTMLVideoElement) => el.currentTime)
  expect(after).toBeGreaterThan(before)
})

// Regression: bits-ui releases the pointer on `document`, so an element-bound
// onpointerup misses an off-track release — leaving the guard stuck open and the
// video stuck paused (the stutter loop returns on replay). A window listener
// must always close the scrub and resume.
test('releasing a scrub off the track resumes playback', async ({ loadApp }) => {
  const page = await loadApp(videoConfig)
  const video = page.locator('video')
  await waitForVideoReady(page)
  await page.locator('button[aria-label="Play"]').click()
  await expect(video).toHaveJSProperty('paused', false)

  const box = await page.locator('[data-seek-slider]').boundingBox()
  if (!box) throw new Error('seek bar not visible')
  await page.mouse.move(box.x + box.width * 0.3, box.y + box.height / 2)
  await page.mouse.down()
  await page.mouse.move(box.x + box.width * 0.6, box.y - 120) // drag off the thin track
  await page.mouse.up() // release OFF the slider element

  // Guard closed via the window listener → playback resumes (not stuck paused).
  await expect(video).toHaveJSProperty('paused', false)
})

test('time display shows formatted time', async ({ loadApp }) => {
  const page = await loadApp(videoConfig)

  await waitForVideoReady(page)

  // Time display should match m:ss pattern
  const timeText = page.locator('.tabular-nums.opacity-80')
  await expect(timeText).toHaveText(/\d+:\d{2}\.\d{2}\s*\/\s*\d+:\d{2}\.\d{2}/)
})