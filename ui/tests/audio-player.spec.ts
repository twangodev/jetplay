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

// IDE-pushed bars must win over the in-browser decode fallback.
test('pushed bars take precedence over the in-browser fallback', async ({ loadApp }) => {
  const page = await loadApp(audioConfig) // decodable URL → fallback fills the bars
  const waveform = page.locator('[aria-label="Seek playback"]')
  await expect(waveform).toBeVisible()
  await expect(async () => {
    expect(Number(await waveform.getAttribute('data-bars'))).toBeGreaterThan(3)
  }).toPass()

  await page.evaluate(() => window.jetplayWaveform?.([0.1, 0.2, 0.3]))
  await expect(waveform).toHaveAttribute('data-bars', '3')
})

// A push that arrived before the page defined the handler is stashed on window
// and must still be picked up on mount.
test('a waveform buffered before load is picked up', async ({ page }) => {
  await page.addInitScript(() => {
    ;(window as any).jetplay = {
      state: 'ready',
      fileName: 'x.mp3',
      fileExtension: 'mp3',
      mediaUrl: '/assets/does-not-exist.mp3',
      isVideo: false,
    }
    ;(window as any).__jetplayWaveform = [0.4, 0.5, 0.6, 0.7]
  })
  await page.goto('/')
  await expect(page.locator('[aria-label="Seek playback"]')).toBeVisible()
})

// The IDE probes the file with FFmpeg and pushes technical metadata; the header
// stays a plain filename until then, and expands into a full grid on click.
test('media-info push renders the summary and expands into a grid', async ({ loadApp }) => {
  const page = await loadApp(audioConfig)
  // No info yet → no toggle, no summary, no grid.
  await expect(page.locator('[aria-label="Toggle media details"]')).toHaveCount(0)
  await expect(page.locator('[data-slot="media-info-summary"]')).toHaveCount(0)

  await page.evaluate(() =>
    window.jetplayMediaInfo?.({
      codec: 'pcm_s16le',
      container: 'wav',
      sampleRateHz: 48000,
      channels: 2,
      channelLabel: 'stereo',
      bitDepth: '16-bit',
      bitrateBps: 1536000,
      durationMs: 42318,
      sizeBytes: 1258291,
    }),
  )

  const summary = page.locator('[data-slot="media-info-summary"]')
  await expect(summary).toBeVisible()
  await expect(summary).toContainText('48 kHz')
  await expect(summary).toContainText('stereo')
  // Container is not duplicated in the summary — the badge already shows it.
  await expect(summary).not.toContainText('WAV')

  // Grid is collapsed until the header is clicked.
  await expect(page.locator('[data-slot="media-info-grid"]')).toHaveCount(0)
  await page.locator('[aria-label="Toggle media details"]').click()

  const grid = page.locator('[data-slot="media-info-grid"]')
  await expect(grid).toBeVisible()
  await expect(grid).toContainText('Codec')
  await expect(grid).toContainText('pcm_s16le')
  // The exact container lives in the grid instead of the summary.
  await expect(grid).toContainText('Container')
  await expect(grid).toContainText('WAV')
  await expect(grid).toContainText('Bitrate')
  await expect(grid).toContainText('1536 kbps')
})

// Embedded tags render in their own group in the expanded panel, and embedded
// cover art becomes a blurred ambient background (not a thumbnail).
test('embedded tags render in the expanded panel and album art blurs the background', async ({ loadApp }) => {
  const page = await loadApp(audioConfig)
  await expect(page.locator('[data-slot="album-art"]')).toHaveCount(0)

  const art =
    'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=='
  await page.evaluate((cover) => {
    window.jetplayMediaInfo?.({
      codec: 'flac',
      container: 'flac',
      sampleRateHz: 44100,
      channels: 2,
      channelLabel: 'stereo',
      tags: [
        { label: 'Title', value: 'Aerodynamic' },
        { label: 'Artist', value: 'Daft Punk' },
        { label: 'Album', value: 'Discovery' },
      ],
      albumArt: cover,
    })
  }, art)

  // Cover art appears as the ambient blurred background layer.
  await expect(page.locator('[data-slot="album-art"]')).toBeVisible()

  // Tags live in their own group, revealed on expand.
  await expect(page.locator('[data-slot="media-info-tags"]')).toHaveCount(0)
  await page.locator('[aria-label="Toggle media details"]').click()
  const tagsPanel = page.locator('[data-slot="media-info-tags"]')
  await expect(tagsPanel).toBeVisible()
  await expect(tagsPanel).toContainText('Title')
  await expect(tagsPanel).toContainText('Aerodynamic')
  await expect(tagsPanel).toContainText('Daft Punk')
})

// Regression: the player container's Space shortcut must not swallow the
// toggle button's native activation. Space on the focused toggle expands the
// inspector and must NOT start playback.
test('space activates the focused media-details toggle, not playback', async ({ loadApp }) => {
  const page = await loadApp(audioConfig)
  await page.evaluate(() =>
    window.jetplayMediaInfo?.({ codec: 'pcm_s16le', container: 'wav', sampleRateHz: 48000, channels: 2, channelLabel: 'stereo' }),
  )
  const toggle = page.locator('[aria-label="Toggle media details"]')
  await toggle.focus()
  await expect(toggle).toHaveAttribute('aria-expanded', 'false')

  await page.keyboard.press('Space')
  await expect(toggle).toHaveAttribute('aria-expanded', 'true')
  await expect(page.locator('[data-slot="media-info-grid"]')).toBeVisible()
  await expect(page.locator('audio')).toHaveJSProperty('paused', true)
})

// A push that arrived before the page defined the handler is stashed on window
// and must still be picked up on mount (same guard as the waveform).
test('media info buffered before load is picked up', async ({ page }) => {
  await page.addInitScript(() => {
    ;(window as any).jetplay = {
      state: 'ready',
      fileName: 'x.wav',
      fileExtension: 'wav',
      mediaUrl: '/assets/does-not-exist.mp3',
      isVideo: false,
    }
    ;(window as any).__jetplayMediaInfo = { container: 'wav', sampleRateHz: 44100, channels: 1, channelLabel: 'mono' }
  })
  await page.goto('/')
  const summary = page.locator('[data-slot="media-info-summary"]')
  await expect(summary).toBeVisible()
  await expect(summary).toContainText('44.1 kHz')
})

test('play button toggles playback', async ({ loadApp }) => {
  const page = await loadApp(audioConfig)
  const playBtn = page.locator('button[aria-label="Play"], button[aria-label="Pause"]')

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