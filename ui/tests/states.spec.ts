import { test, expect } from './fixtures'

test('audio player renders in ready state', async ({ loadApp }) => {
  const page = await loadApp({
    state: 'ready',
    fileName: 'sintel.ogg',
    fileExtension: 'ogg',
    mediaUrl: '/assets/sintel.ogg',
    isVideo: false,
  })

  await expect(page.locator('audio')).toBeAttached()
  await expect(page.getByText('sintel.ogg')).toBeVisible()
  // Play button (the round one with Play icon) should be visible
  await expect(page.locator('button[aria-label="Play"], button[aria-label="Pause"]')).toBeVisible()
})

test('video player renders in ready state', async ({ loadApp }) => {
  const page = await loadApp({
    state: 'ready',
    fileName: 'sintel.webm',
    fileExtension: 'webm',
    mediaUrl: '/assets/sintel.webm',
    isVideo: true,
  })

  await expect(page.locator('video')).toBeAttached()
})

test('transcoding state shows progress and tip', async ({ loadApp }) => {
  const page = await loadApp({
    state: 'loading',
    fileName: 'track.aac',
    transcodingReason: 'AAC needs conversion',
  })

  await expect(page.getByText('Converting for playback\u2026')).toBeVisible()
  await expect(page.getByText('AAC needs conversion')).toBeVisible()
  await expect(page.getByText(/\.webm.*\.ogg.*\.opus/)).toBeVisible()
  await expect(page.getByText('track.aac')).toBeVisible()
})

test('error state shows message', async ({ loadApp }) => {
  const page = await loadApp({
    state: 'error',
    errorMessage: 'Codec not supported',
  })

  await expect(page.getByText('Unable to play this file')).toBeVisible()
  await expect(page.getByText('Codec not supported')).toBeVisible()
})

test('custom UI strings override defaults', async ({ loadApp }) => {
  const page = await loadApp({
    state: 'error',
    errorMessage: 'Something went wrong',
    ui: { errorTitle: 'Custom Error Title' },
  })

  await expect(page.getByText('Custom Error Title')).toBeVisible()
  await expect(page.getByText('Something went wrong')).toBeVisible()
})

test('extension badge displays in audio player', async ({ loadApp }) => {
  const page = await loadApp({
    state: 'ready',
    fileName: 'track.mp3',
    fileExtension: 'mp3',
    mediaUrl: '/assets/sintel.mp3',
    isVideo: false,
  })

  await expect(page.getByText('mp3', { exact: true })).toBeVisible()
})

// Regression: a fast transcode can call jetplayReady before the app has mounted,
// so the handler call is a no-op and it sticks on "Converting…" at 0%. The IDE
// stashes the ready URL on window; the app must seed from it on mount.
test('a media-ready buffered before mount leaves the converting screen', async ({ page }) => {
  await page.addInitScript(() => {
    ;(window as any).jetplay = { state: 'loading', fileName: 'clip.mp4', fileExtension: 'mp4', isVideo: true }
    ;(window as any).__jetplayReadyUrl = '/assets/sintel.webm'
  })
  await page.goto('/')
  await expect(page.getByText('Converting for playback…')).toHaveCount(0)
  await expect(page.locator('video')).toBeAttached()
})

test('an error buffered before mount shows the error state', async ({ page }) => {
  await page.addInitScript(() => {
    ;(window as any).jetplay = { state: 'loading', fileName: 'x.mp4', fileExtension: 'mp4' }
    ;(window as any).__jetplayError = 'Codec not supported'
  })
  await page.goto('/')
  await expect(page.getByText('Codec not supported')).toBeVisible()
})