import { test as base, type Page } from '@playwright/test'

type JetplayConfig = NonNullable<Window['jetplay']>

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