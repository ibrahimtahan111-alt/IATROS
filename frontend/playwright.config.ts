import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests (decisions.md D-040) drive the real application against a running backend with the fictional
 * demo clinic (README, "End-to-end tests"). They share that clinic's data, so they run one at a time.
 */
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:5173'

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    locale: 'en-US',
    colorScheme: 'light',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'setup', testMatch: /global\.setup\.ts/, teardown: 'teardown' },
    { name: 'teardown', testMatch: /global\.teardown\.ts/ },
    { name: 'chromium', use: { ...devices['Desktop Chrome'] }, dependencies: ['setup'] },
  ],
  webServer: {
    command: 'npm run dev',
    url: baseURL,
    reuseExistingServer: true,
    timeout: 120_000,
  },
})
