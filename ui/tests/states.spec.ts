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
  await expect(page.locator('button.rounded-full')).toBeVisible()
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

test('downloading state shows progress and file name', async ({ loadApp }) => {
  const page = await loadApp({
    state: 'downloading',
    fileName: 'big-file.mp4',
  })

  await expect(page.getByText('Downloading\u2026')).toBeVisible()
  await expect(page.getByText('big-file.mp4')).toBeVisible()
  // Progress bar container exists
  await expect(page.locator('.progress-fill')).toBeAttached()
})

test('downloading state shows reason when provided', async ({ loadApp }) => {
  const page = await loadApp({
    state: 'downloading',
    fileName: 'remote.mp4',
    downloadingReason: 'Remote file needs to be downloaded',
  })

  await expect(page.getByText('Remote file needs to be downloaded')).toBeVisible()
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