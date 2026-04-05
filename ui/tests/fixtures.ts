import { test as base, type Page } from '@playwright/test'

interface JetplayConfig {
  mediaUrl?: string
  fileName?: string
  fileExtension?: string
  isVideo?: boolean
  state?: 'downloading' | 'loading' | 'ready' | 'error'
  errorMessage?: string
  transcodingReason?: string
  downloadingReason?: string
  ui?: {
    downloadingLabel?: string
    transcodingLabel?: string
    transcodingTip?: string
    errorTitle?: string
  }
}

export const test = base.extend<{ loadApp: (config: JetplayConfig) => Promise<Page> }>({
  loadApp: async ({ page }, use) => {
    await use(async (config: JetplayConfig) => {
      await page.addInitScript((cfg) => {
        ;(window as any).jetplay = cfg
      }, config)
      await page.goto('/')
      return page
    })
  },
})

export { expect } from '@playwright/test'