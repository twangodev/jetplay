import { test, expect } from './fixtures'
import type { Page } from '@playwright/test'

type Preferences = { volume: number; muted: boolean; playbackRate: number }
type PlaybackMemory = { preferences: Preferences; positionSeconds: number }
type PlaybackUpdate = { preferences?: Partial<Preferences>; positionSeconds?: number }
type MemoryWindow = Window & {
  __savedPlaybackMemory: PlaybackMemory
  __playbackMemoryWrites: PlaybackUpdate[]
}

const preferences: Preferences = { volume: 0.35, muted: true, playbackRate: 1.5 }

async function loadWithMemory(
  page: Page,
  config: NonNullable<Window['jetplay']>,
  memory: PlaybackMemory,
  rejectWrites = false,
) {
  await page.addInitScript(({ config, memory, rejectWrites }) => {
    window.jetplay = { ...config, playbackMemory: memory }
    const harness = window as MemoryWindow
    harness.__savedPlaybackMemory = structuredClone(memory)
    harness.__playbackMemoryWrites = []
    window.jetplaySavePlayback = async (update) => {
      harness.__playbackMemoryWrites.push(structuredClone(update))
      if (rejectWrites) throw new Error('Playback storage unavailable')
      if (update.preferences) harness.__savedPlaybackMemory.preferences = { ...harness.__savedPlaybackMemory.preferences, ...update.preferences }
      if (update.positionSeconds !== undefined) harness.__savedPlaybackMemory.positionSeconds = update.positionSeconds
    }
  }, { config, memory, rejectWrites })
  await page.goto('/', { waitUntil: 'domcontentloaded' })
}

async function jumpTo(page: Page, timestamp: string) {
  await page.getByRole('button', { name: 'Playback actions', exact: true }).click()
  await page.getByRole('menuitem', { name: /Jump to timestamp/ }).click()
  const dialog = page.getByRole('dialog', { name: 'Jump to timestamp' })
  await dialog.getByRole('textbox', { name: 'Timestamp', exact: true }).fill(timestamp)
  await dialog.getByRole('button', { name: 'Jump', exact: true }).click()
  await expect(dialog).not.toBeVisible()
}

for (const kind of ['audio', 'video'] as const) {
  test.describe(`${kind} playback memory`, () => {
    const config = {
      state: 'ready' as const,
      fileName: `original.${kind === 'audio' ? 'aac' : 'mp4'}`,
      filePath: `assets/original.${kind === 'audio' ? 'aac' : 'mp4'}`,
      fileExtension: kind === 'audio' ? 'aac' : 'mp4',
      mediaUrl: `/assets/sintel.${kind === 'audio' ? 'ogg' : 'webm'}`,
      isVideo: kind === 'video',
    }

    test('restores volume, mute, speed, and position without autoplay and reflects them in controls', async ({ page }) => {
      await loadWithMemory(page, config, { preferences, positionSeconds: 2.345 })
      const media = page.locator(kind)
      await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.currentTime)).toBeCloseTo(2.345, 2)
      await expect(media).toHaveJSProperty('volume', preferences.volume)
      await expect(media).toHaveJSProperty('muted', true)
      await expect(media).toHaveJSProperty('playbackRate', preferences.playbackRate)
      await expect(media).toHaveJSProperty('paused', true)
      const volume = page.locator('[aria-label="Volume"] [role="slider"]')
      await expect(volume).toHaveAttribute('aria-valuenow', '0')

      await page.getByRole('button', { name: 'Toggle mute', exact: true }).click()
      await expect(media).toHaveJSProperty('muted', false)
      await expect(media).toHaveJSProperty('volume', preferences.volume)
      await expect(volume).toHaveAttribute('aria-valuenow', '0.35')
      if (kind === 'audio') await expect(page.getByText('35%', { exact: true })).toBeVisible()

      await page.getByRole('button', { name: 'Playback speed', exact: true }).click()
      await expect(page.getByRole('menuitem', { name: '1.5x', exact: true }).locator('svg')).toBeVisible()
      await page.keyboard.press('Escape')
      await expect(media).toHaveJSProperty('paused', true)
    })

    test('preference changes made while metadata is loading survive readiness', async ({ page }) => {
      let release!: () => void
      const gate = new Promise<void>((resolve) => { release = resolve })
      await page.route(`**${config.mediaUrl}`, async (route) => {
        await gate
        await route.continue()
      })
      try {
        await loadWithMemory(page, config, { preferences, positionSeconds: 2.345 })
        const media = page.locator(kind)
        await expect(media).toHaveJSProperty('readyState', 0)
        await page.getByRole('button', { name: 'Toggle mute', exact: true }).click()
        await page.locator('[aria-label="Volume"] [role="slider"]').press('End')
        await page.getByRole('button', { name: 'Playback speed', exact: true }).click()
        await page.getByRole('menuitem', { name: '2x', exact: true }).click()
        await expect.poll(() => page.evaluate(() => (window as MemoryWindow).__savedPlaybackMemory.preferences))
          .toEqual({ volume: 1, muted: false, playbackRate: 2 })

        release()
        await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.readyState)).toBeGreaterThanOrEqual(2)
        await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.currentTime)).toBeCloseTo(2.345, 2)
        await expect(media).toHaveJSProperty('volume', 1)
        await expect(media).toHaveJSProperty('muted', false)
        await expect(media).toHaveJSProperty('playbackRate', 2)
        await expect(media).toHaveJSProperty('paused', true)
      } finally {
        release()
      }
    })

    test('changing preferences preserves the file position', async ({ page }) => {
      await loadWithMemory(page, config, { preferences, positionSeconds: 2.345 })
      const media = page.locator(kind)
      await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.currentTime)).toBeCloseTo(2.345, 2)
      await expect(media).toHaveJSProperty('seeking', false)
      await page.evaluate(() => { (window as MemoryWindow).__playbackMemoryWrites = [] })

      await page.getByRole('button', { name: 'Toggle mute', exact: true }).click()
      await page.getByRole('button', { name: 'Playback speed', exact: true }).click()
      await page.getByRole('menuitem', { name: '2x', exact: true }).click()
      await expect.poll(() => page.evaluate(() => (window as MemoryWindow).__savedPlaybackMemory.preferences))
        .toEqual({ volume: 0.35, muted: false, playbackRate: 2 })
      await expect.poll(() => page.evaluate(() => (window as MemoryWindow).__savedPlaybackMemory.positionSeconds))
        .toBeCloseTo(2.345, 2)
      const writes = await page.evaluate(() => (window as MemoryWindow).__playbackMemoryWrites)
      expect(writes.some((update) => update.preferences !== undefined)).toBe(true)
      expect(writes.filter((update) => update.preferences !== undefined).every((update) => update.positionSeconds === undefined)).toBe(true)
      await expect(media).toHaveJSProperty('paused', true)
    })

    test('changing one preference in an older tab preserves other tabs changes', async ({ page, context }) => {
      const second = await context.newPage()
      let sharedPreferences: Preferences = { volume: 1, muted: false, playbackRate: 1 }
      const initialize = async (tab: Page) => {
        await tab.exposeFunction('saveSharedPlayback', (update: PlaybackUpdate) => {
          if (update.preferences) sharedPreferences = { ...sharedPreferences, ...update.preferences }
        })
        await tab.addInitScript((config) => {
          window.jetplay = {
            ...config,
            playbackMemory: { preferences: { volume: 1, muted: false, playbackRate: 1 }, positionSeconds: 0 },
          }
          window.jetplaySavePlayback = (update) => (window as any).saveSharedPlayback(update)
        }, config)
        await tab.goto('/')
        await expect.poll(() => tab.locator(kind).evaluate((el: HTMLMediaElement) => el.readyState)).toBeGreaterThanOrEqual(2)
      }
      try {
        await initialize(page)
        await initialize(second)
        await second.locator(kind).evaluate((el: HTMLMediaElement) => { el.volume = 0.2 })
        await expect.poll(() => sharedPreferences.volume).toBe(0.2)
        await page.bringToFront()
        await page.getByRole('button', { name: 'Toggle mute', exact: true }).click()
        await expect.poll(() => sharedPreferences).toEqual({ volume: 0.2, muted: true, playbackRate: 1 })
        await second.bringToFront()
        await second.getByRole('button', { name: 'Playback speed', exact: true }).click()
        await second.getByRole('menuitem', { name: '2x', exact: true }).click()
        await expect.poll(() => sharedPreferences).toEqual({ volume: 0.2, muted: true, playbackRate: 2 })
      } finally {
        await second.close()
      }
    })

    test('seeking saves the new position without replacing preferences', async ({ page }) => {
      await loadWithMemory(page, config, { preferences, positionSeconds: 1.25 })
      const media = page.locator(kind)
      await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.currentTime)).toBeCloseTo(1.25, 2)
      await expect(media).toHaveJSProperty('seeking', false)
      await page.evaluate(() => { (window as MemoryWindow).__playbackMemoryWrites = [] })

      await jumpTo(page, '3.456')
      await expect.poll(() => page.evaluate(() => (window as MemoryWindow).__savedPlaybackMemory.positionSeconds))
        .toBeCloseTo(3.456, 2)
      expect(await page.evaluate(() => (window as MemoryWindow).__savedPlaybackMemory.preferences)).toEqual(preferences)
      const writes = await page.evaluate(() => (window as MemoryWindow).__playbackMemoryWrites)
      expect(writes.some((update) => update.positionSeconds !== undefined)).toBe(true)
      expect(writes.every((update) => update.preferences === undefined)).toBe(true)
      await expect(media).toHaveJSProperty('paused', true)
    })

    test('pausing saves the current playback position', async ({ page }) => {
      await loadWithMemory(page, config, { preferences, positionSeconds: 1 })
      const media = page.locator(kind)
      await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.currentTime)).toBeCloseTo(1, 2)
      await page.getByRole('button', { name: 'Play', exact: true }).click()
      await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.currentTime)).toBeGreaterThan(1.1)
      await page.getByRole('button', { name: 'Pause', exact: true }).click()
      const stoppedAt = await media.evaluate((el: HTMLMediaElement) => el.currentTime)
      await expect.poll(() => page.evaluate(() => (window as MemoryWindow).__savedPlaybackMemory.positionSeconds))
        .toBeCloseTo(stoppedAt, 2)
      await expect(media).toHaveJSProperty('paused', true)
    })

    test('finishing normally resets the saved position to the start', async ({ page }) => {
      await loadWithMemory(page, config, { preferences, positionSeconds: 1.25 })
      const media = page.locator(kind)
      await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.currentTime)).toBeCloseTo(1.25, 2)
      await media.evaluate(async (el: HTMLMediaElement) => {
        el.currentTime = el.duration - 0.1
        await el.play()
      })
      await expect(media).toHaveJSProperty('ended', true)
      await expect.poll(() => page.evaluate(() => (window as MemoryWindow).__savedPlaybackMemory.positionSeconds)).toBe(0)
      expect(await page.evaluate(() => (window as MemoryWindow).__savedPlaybackMemory.preferences)).toEqual(preferences)
    })

    for (const savedPosition of [-3, 99_999]) {
      test(`ignores invalid saved position ${savedPosition}`, async ({ page }) => {
        await loadWithMemory(page, config, { preferences, positionSeconds: savedPosition })
        const media = page.locator(kind)
        await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.readyState)).toBeGreaterThanOrEqual(2)
        await expect(media).toHaveJSProperty('currentTime', 0)
        await expect(media).toHaveJSProperty('paused', true)
        await expect.poll(() => page.evaluate(() => (window as MemoryWindow).__savedPlaybackMemory.positionSeconds)).toBe(0)
      })
    }

    test('late duration updates do not undo a user seek', async ({ page }) => {
      await loadWithMemory(page, config, { preferences, positionSeconds: 1.25 })
      const media = page.locator(kind)
      await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.currentTime)).toBeCloseTo(1.25, 2)
      await jumpTo(page, '3.456')
      await expect(media).toHaveJSProperty('seeking', false)
      await media.evaluate((el: HTMLMediaElement) => el.dispatchEvent(new Event('durationchange')))
      await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.currentTime)).toBeCloseTo(3.456, 2)
      await expect(media).toHaveJSProperty('paused', true)
    })

    test('different original files retain independent positions even with the same playback URL', async ({ page, context }) => {
      const second = await context.newPage()
      try {
        await loadWithMemory(page, config, { preferences, positionSeconds: 1.25 })
        await loadWithMemory(second, { ...config, filePath: 'assets/second-original.mkv' }, { preferences, positionSeconds: 3.75 })
        await expect.poll(() => page.locator(kind).evaluate((el: HTMLMediaElement) => el.currentTime)).toBeCloseTo(1.25, 2)
        await expect.poll(() => second.locator(kind).evaluate((el: HTMLMediaElement) => el.currentTime)).toBeCloseTo(3.75, 2)
        await page.bringToFront()
        await jumpTo(page, '4.5')
        await expect.poll(() => page.evaluate(() => (window as MemoryWindow).__savedPlaybackMemory.positionSeconds)).toBeCloseTo(4.5, 2)
        expect(await second.evaluate(() => (window as MemoryWindow).__savedPlaybackMemory.positionSeconds)).toBe(3.75)
        await expect(second.locator(kind)).toHaveJSProperty('paused', true)
      } finally {
        await second.close()
      }
    })

    test('storage failure does not prevent changing controls or playing', async ({ page }) => {
      const errors: string[] = []
      page.on('pageerror', (error) => errors.push(error.message))
      await loadWithMemory(page, config, { preferences, positionSeconds: 1.25 }, true)
      const media = page.locator(kind)
      await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.currentTime)).toBeCloseTo(1.25, 2)
      await page.getByRole('button', { name: 'Playback speed', exact: true }).click()
      await page.getByRole('menuitem', { name: '2x', exact: true }).click()
      await jumpTo(page, '3.456')
      await page.getByRole('button', { name: 'Play', exact: true }).click()
      await expect(media).toHaveJSProperty('paused', false)
      await expect(media).toHaveJSProperty('playbackRate', 2)
      await page.getByRole('button', { name: 'Pause', exact: true }).click()
      expect(await page.evaluate(() => (window as MemoryWindow).__playbackMemoryWrites.length)).toBeGreaterThan(0)
      expect(errors).toEqual([])
    })

    test('standalone preview remembers preferences and position across reload without autoplay', async ({ page }) => {
      await page.addInitScript((config) => { window.jetplay = config }, config)
      await page.goto('/')
      const media = page.locator(kind)
      await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.readyState)).toBeGreaterThanOrEqual(2)
      await page.getByRole('button', { name: 'Toggle mute', exact: true }).click()
      await page.getByRole('button', { name: 'Playback speed', exact: true }).click()
      await page.getByRole('menuitem', { name: '1.5x', exact: true }).click()
      await jumpTo(page, '2.345')
      await expect(media).toHaveJSProperty('seeking', false)
      await page.reload()
      await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.currentTime)).toBeCloseTo(2.345, 2)
      await expect(media).toHaveJSProperty('muted', true)
      await expect(media).toHaveJSProperty('playbackRate', 1.5)
      await expect(media).toHaveJSProperty('paused', true)
    })
  })
}
