import { test, expect } from './fixtures'

const audioConfig = {
  state: 'ready' as const,
  fileName: 'sintel.ogg',
  filePath: 'assets/sintel.ogg',
  fileExtension: 'ogg',
  mediaUrl: '/assets/sintel.ogg',
  isVideo: false,
}

test('Pause immediately cancels a pending audio start without an unhandled rejection', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  let release!: () => void
  const pending = new Promise<void>((resolve) => { release = resolve })
  await page.route('**/assets/sintel.ogg', async (route) => {
    await pending
    await route.continue()
  })
  await page.addInitScript((config) => { window.jetplay = config }, audioConfig)
  try {
    await page.goto('/', { waitUntil: 'domcontentloaded' })
    const audio = page.locator('audio')
    await page.getByRole('button', { name: 'Play', exact: true }).click()
    await expect(audio).toHaveJSProperty('paused', false)
    await page.getByRole('button', { name: 'Pause', exact: true }).click()
    await expect(audio).toHaveJSProperty('paused', true)
    release()
    await expect.poll(() => audio.evaluate((el: HTMLAudioElement) => el.readyState)).toBeGreaterThanOrEqual(2)
    await expect(audio).toHaveJSProperty('paused', true)
    await expect(page.getByText('Unable to play this file', { exact: true })).toHaveCount(0)
    expect(errors).toEqual([])
  } finally {
    release()
  }
})

test('missing audio displays the same media error screen as video', async ({ page, loadApp }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.route('**/assets/sintel.ogg', (route) => route.fulfill({ status: 404, body: 'not found' }))
  await loadApp(audioConfig)
  await expect(page.getByText('Unable to play this file', { exact: true })).toBeVisible()
  await expect(page.getByText('This media format or codec is not supported.', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Play', exact: true })).toHaveCount(0)
  expect(errors).toEqual([])
})

test('a rejected audio play request is reported without an unhandled rejection', async ({ page, loadApp }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await loadApp(audioConfig)
  await page.locator('audio').evaluate((el: HTMLAudioElement) => {
    el.play = () => Promise.reject(new DOMException('Playback denied', 'NotAllowedError'))
  })
  await page.getByRole('button', { name: 'Play', exact: true }).click()
  await expect(page.getByText('Unable to play this file', { exact: true })).toBeVisible()
  expect(errors).toEqual([])
})

test('scratch output shares mute and volume, including changes during a drag', async ({ page, loadApp }) => {
  await page.addInitScript(() => {
    const harness = window as any
    harness.scratchStarts = []
    harness.scratchDecoded = false
    const decode = AudioContext.prototype.decodeAudioData
    AudioContext.prototype.decodeAudioData = async function (data: ArrayBuffer) {
      const buffer = await decode.call(this, data)
      harness.scratchDecoded = true
      return buffer
    }
    const connect = AudioNode.prototype.connect
    AudioNode.prototype.connect = function (destination: AudioNode, ...rest: any[]) {
      ;(this as any).reviewDestination = destination
      return (connect as any).call(this, destination, ...rest)
    }
    const createSource = AudioContext.prototype.createBufferSource
    AudioContext.prototype.createBufferSource = function () {
      const source = createSource.call(this)
      const start = source.start
      source.start = function (...args: Parameters<AudioBufferSourceNode['start']>) {
        const filter = (source as any).reviewDestination
        const output = filter?.reviewDestination
        harness.scratchGain = output instanceof GainNode ? output : null
        harness.scratchStarts.push({
          gain: output instanceof GainNode ? output.gain.value : null,
          muted: document.querySelector('audio')?.muted,
          volume: document.querySelector('audio')?.volume,
        })
        start.apply(source, args)
      }
      return source
    }
  })
  await loadApp({ ...audioConfig, waveform: Array(80).fill(0.5) })
  const audio = page.locator('audio')
  await expect.poll(() => audio.evaluate((el: HTMLAudioElement) => el.readyState)).toBeGreaterThanOrEqual(2)
  await audio.evaluate((el: HTMLAudioElement) => { el.volume = 0.25 })
  const waveform = page.getByRole('slider', { name: 'Seek playback', exact: true })
  const box = await waveform.boundingBox()
  if (!box) throw new Error('Waveform not visible')
  const y = box.y + box.height / 2
  await page.mouse.move(box.x + box.width * 0.7, y)
  await page.mouse.down()
  await page.mouse.up()
  await page.waitForFunction(() => (window as any).scratchDecoded)

  await page.mouse.move(box.x + box.width * 0.7, y)
  await page.mouse.down()
  await page.mouse.move(box.x + box.width * 0.5, y, { steps: 10 })
  await expect.poll(() => page.evaluate(() => (window as any).scratchStarts.some((start: any) => start.gain === 0.25)))
    .toBe(true)

  await audio.evaluate((el: HTMLAudioElement) => { el.muted = true })
  await page.waitForFunction(() => (window as any).scratchGain?.gain.value === 0)
  await page.mouse.move(box.x + box.width * 0.3, y, { steps: 10 })
  await expect.poll(() => page.evaluate(() => (window as any).scratchStarts.some((start: any) => start.muted && start.gain === 0)))
    .toBe(true)

  await audio.evaluate((el: HTMLAudioElement) => { el.volume = 0.1; el.muted = false })
  await page.waitForFunction(() => Math.abs((window as any).scratchGain?.gain.value - 0.1) < 0.0001)
  await page.mouse.move(box.x + box.width * 0.2, y, { steps: 10 })
  await page.mouse.up()
  await expect.poll(() => page.evaluate(() => (window as any).scratchStarts.some((start: any) => !start.muted && Math.abs(start.gain - 0.1) < 0.0001)))
    .toBe(true)
  const starts = await page.evaluate(() => (window as any).scratchStarts as { gain: number | null }[])
  expect(starts.length).toBeGreaterThan(0)
  expect(starts.every((start) => start.gain !== null)).toBe(true)
})
