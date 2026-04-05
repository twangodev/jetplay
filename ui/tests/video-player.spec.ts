import { test, expect } from './fixtures'

const videoConfig = {
  state: 'ready' as const,
  fileName: 'sintel.webm',
  fileExtension: 'webm',
  mediaUrl: '/assets/sintel.webm',
  isVideo: true,
}

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

  // The overlay play button is inside the controls bar (not the round one like audio)
  const playBtn = page.locator('.bg-gradient-to-t button').first()
  await expect(playBtn).toBeVisible()

  await playBtn.click()
  await expect(video).toHaveJSProperty('paused', false)

  await playBtn.click()
  await expect(video).toHaveJSProperty('paused', true)
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

  // Wait for media to load
  await page.waitForFunction(() => {
    const el = document.querySelector('video')
    return el && el.duration > 0
  })

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

test('time display shows formatted time', async ({ loadApp }) => {
  const page = await loadApp(videoConfig)

  // Wait for duration to load
  await page.waitForFunction(() => {
    const el = document.querySelector('video')
    return el && el.duration > 0
  })

  // Time display should match m:ss pattern
  const timeText = page.locator('.tabular-nums.opacity-80')
  await expect(timeText).toHaveText(/\d+:\d{2}\.\d{2}\s*\/\s*\d+:\d{2}\.\d{2}/)
})