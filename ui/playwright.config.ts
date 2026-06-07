import { defineConfig } from '@playwright/test'

// Override with JETPLAY_TEST_PORT when 5173 is taken by another dev server —
// otherwise reuseExistingServer would latch onto that foreign server.
const port = Number(process.env.JETPLAY_TEST_PORT ?? 5173)

export default defineConfig({
  testDir: './tests',
  timeout: 15_000,
  retries: process.env.CI ? 1 : 0,
  use: {
    baseURL: `http://localhost:${port}`,
    browserName: 'chromium',
  },
  webServer: {
    command: `npm run dev -- --port ${port} --strictPort`,
    port,
    reuseExistingServer: !process.env.CI,
  },
})