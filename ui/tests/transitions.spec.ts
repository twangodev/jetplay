import { test, expect } from './fixtures'

test('jetplayReady transitions to audio player', async ({ loadApp }) => {
  const page = await loadApp({
    state: 'downloading',
    fileName: 'track.ogg',
    fileExtension: 'ogg',
    isVideo: false,
  })

  await expect(page.locator('audio')).not.toBeAttached()

  await page.evaluate(() => {
    window.jetplayReady?.('/assets/sintel.ogg')
  })

  await expect(page.locator('audio')).toBeAttached()
})

test('jetplayReady transitions to video player', async ({ loadApp }) => {
  const page = await loadApp({
    state: 'loading',
    fileName: 'clip.webm',
    fileExtension: 'webm',
    isVideo: true,
  })

  await expect(page.locator('video')).not.toBeAttached()

  await page.evaluate(() => {
    window.jetplayReady?.('/assets/sintel.webm')
  })

  await expect(page.locator('video')).toBeAttached()
})

test('jetplayStartTranscoding transitions from downloading to loading', async ({ loadApp }) => {
  const page = await loadApp({
    state: 'downloading',
    fileName: 'track.aac',
  })

  await expect(page.getByText('Downloading\u2026')).toBeVisible()

  await page.evaluate(() => {
    window.jetplayStartTranscoding?.()
  })

  await expect(page.getByText('Converting for playback\u2026')).toBeVisible()
})

test('jetplayError transitions to error state', async ({ loadApp }) => {
  const page = await loadApp({
    state: 'ready',
    fileName: 'clip.webm',
    mediaUrl: '/assets/sintel.webm',
    isVideo: true,
  })

  await page.evaluate(() => {
    window.jetplayError?.('Playback failed unexpectedly')
  })

  await expect(page.getByText('Unable to play this file')).toBeVisible()
  await expect(page.getByText('Playback failed unexpectedly')).toBeVisible()
})

test('jetplayUpdateProgress updates transcoding progress', async ({ loadApp }) => {
  const page = await loadApp({
    state: 'loading',
    fileName: 'track.aac',
  })

  await page.evaluate(() => {
    window.jetplayUpdateProgress?.(50)
  })

  await expect(page.getByText('50%')).toBeVisible()
})

test('jetplayUpdateDownloadProgress updates download progress', async ({ loadApp }) => {
  const page = await loadApp({
    state: 'downloading',
    fileName: 'big.mp4',
  })

  await page.evaluate(() => {
    window.jetplayUpdateDownloadProgress?.(75)
  })

  await expect(page.getByText('75%')).toBeVisible()
})