import { defineConfig, devices } from '@playwright/test';

const baseURL = process.env.BANKSIM_URL ?? 'https://bank.localtest.me';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    locale: 'nl-NL',
    timezoneId: 'Europe/Amsterdam',
    // De BankSim-CA moet vertrouwd zijn (deploy/scripts/trust-ca.sh); alleen lokaal te omzeilen.
    ignoreHTTPSErrors: process.env.BANKSIM_IGNORE_HTTPS_ERRORS === 'true',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],
});
