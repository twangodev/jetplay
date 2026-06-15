import { test, expect } from './fixtures'

test('jetplayReady transitions to audio player', async ({ loadApp }) => {
  const page = await loadApp({
    state: 'loading',
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