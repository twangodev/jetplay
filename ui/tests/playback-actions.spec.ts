import { test, expect } from './fixtures'

for (const kind of ['audio', 'video'] as const) {
  test.describe(`${kind} playback actions`, () => {
    const config = {
      state: 'ready' as const,
      fileName: `sintel.${kind === 'audio' ? 'ogg' : 'webm'}`,
      filePath: `assets/original.${kind === 'audio' ? 'aac' : 'mp4'}`,
      fileExtension: kind === 'audio' ? 'ogg' : 'webm',
      mediaUrl: `/assets/sintel.${kind === 'audio' ? 'ogg' : 'webm'}`,
      isVideo: kind === 'video',
    }

    test('repeat continues past the end and can be switched off', async ({ loadApp }) => {
      const page = await loadApp(config)
      const media = page.locator(kind)
      await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.readyState)).toBeGreaterThanOrEqual(2)
      await page.getByRole('button', { name: 'Playback actions', exact: true }).click()
      await page.getByRole('menuitemcheckbox', { name: 'Repeat file' }).click()
      await expect(media).toHaveJSProperty('loop', true)
      await media.evaluate(async (el: HTMLMediaElement) => {
        el.muted = true
        el.currentTime = el.duration - 0.15
        await el.play()
      })
      await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.currentTime)).toBeLessThan(2)
      await expect(media).toHaveJSProperty('paused', false)
      await media.evaluate((el: HTMLMediaElement) => el.pause())
      await page.getByRole('button', { name: 'Playback actions', exact: true }).click()
      const repeat = page.getByRole('menuitemcheckbox', { name: 'Repeat file' })
      await expect(repeat).toHaveAttribute('aria-checked', 'true')
      await repeat.click()
      await expect(media).toHaveJSProperty('loop', false)
    })

    for (const timestamp of ['2.345', '00:02.345', '0:00:02.345']) {
      test(`jumps precisely to ${timestamp} without starting playback`, async ({ loadApp }) => {
        const page = await loadApp(config)
        const media = page.locator(kind)
        await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.readyState)).toBeGreaterThanOrEqual(2)
        await page.getByRole('button', { name: 'Playback actions', exact: true }).click()
        await page.getByRole('menuitem', { name: /Jump to timestamp/ }).click()
        const dialog = page.getByRole('dialog', { name: 'Jump to timestamp' })
        const input = dialog.getByRole('textbox', { name: 'Timestamp', exact: true })
        await expect(input).toBeFocused()
        await input.fill(timestamp)
        await input.press('Enter')
        await expect(dialog).not.toBeVisible()
        await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.currentTime)).toBeCloseTo(2.345, 2)
        await expect(media).toHaveJSProperty('paused', true)
        await expect(page.getByRole('button', { name: 'Playback actions', exact: true })).toBeFocused()
      })
    }

    test('invalid and out-of-range timestamps do not seek; Escape restores focus', async ({ loadApp }) => {
      const page = await loadApp(config)
      const media = page.locator(kind)
      await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.readyState)).toBeGreaterThanOrEqual(2)
      await page.getByRole('button', { name: 'Playback actions', exact: true }).click()
      await page.getByRole('menuitem', { name: /Jump to timestamp/ }).click()
      const dialog = page.getByRole('dialog', { name: 'Jump to timestamp' })
      const input = dialog.getByRole('textbox', { name: 'Timestamp', exact: true })
      for (const invalid of ['-1', '1:99', '2.3456', '99:59:59']) {
        await input.fill(invalid)
        await input.press('Enter')
        await expect(dialog).toBeVisible()
        await expect(input).toHaveAttribute('aria-invalid', 'true')
        await expect(media).toHaveJSProperty('currentTime', 0)
      }
      await input.press('Escape')
      await expect(dialog).not.toBeVisible()
      await expect(page.getByRole('button', { name: 'Playback actions', exact: true })).toBeFocused()
      await expect(media).toHaveJSProperty('paused', true)
    })

    test('jump preserves active playback', async ({ loadApp }) => {
      const page = await loadApp(config)
      const media = page.locator(kind)
      await media.evaluate(async (el: HTMLMediaElement) => {
        el.muted = true
        await el.play()
      })
      await page.getByRole('button', { name: 'Playback actions', exact: true }).click()
      await page.getByRole('menuitem', { name: /Jump to timestamp/ }).click()
      const dialog = page.getByRole('dialog', { name: 'Jump to timestamp' })
      await dialog.getByRole('textbox', { name: 'Timestamp', exact: true }).fill('2.345')
      await dialog.getByRole('button', { name: 'Jump', exact: true }).click()
      await expect(dialog).not.toBeVisible()
      await expect(media).toHaveJSProperty('paused', false)
    })

    test('copies original source path and millisecond timestamp through the IDE bridge', async ({ loadApp }) => {
      const page = await loadApp(config)
      const media = page.locator(kind)
      await expect.poll(() => media.evaluate((el: HTMLMediaElement) => el.readyState)).toBeGreaterThanOrEqual(2)
      await page.evaluate(() => {
        window.jetplayCopyText = async (text) => { (window as any).copiedTimestamp = text }
      })
      await media.evaluate((el: HTMLMediaElement) => { el.currentTime = 2.345 })
      await page.getByRole('button', { name: 'Playback actions', exact: true }).click()
      await page.getByRole('menuitem', { name: 'Copy timestamp', exact: true }).click()
      await expect.poll(() => page.evaluate(() => (window as any).copiedTimestamp))
        .toBe(`${config.filePath} @ 00:02.345`)
      await expect(page.getByRole('status')).toContainText(/copied/i)
    })

    test('clipboard rejection is reported without a false success', async ({ loadApp }) => {
      const page = await loadApp(config)
      await expect.poll(() => page.locator(kind).evaluate((el: HTMLMediaElement) => el.readyState)).toBeGreaterThanOrEqual(2)
      await page.evaluate(() => {
        window.jetplayCopyText = async () => { throw new Error('Clipboard unavailable') }
      })
      await page.getByRole('button', { name: 'Playback actions', exact: true }).click()
      await page.getByRole('menuitem', { name: 'Copy timestamp', exact: true }).click()
      await expect(page.getByRole('status')).toContainText(/could not|unable|failed/i)
      await expect(page.locator(kind)).toHaveJSProperty('paused', true)
    })
  })
}
