import { defineConfig, devices } from '@playwright/test';

/**
 * E2E-tests tegen de installatie in namespace banksim (TO §14). Eén keer per run worden de testdata en de
 * simulatiedatum teruggezet (global-setup) en logt de setup in als klant en beheerder; die sessies hergebruiken
 * alle browsers. Tests delen data, dus ze draaien na elkaar.
 */
const baseURL = process.env.BANKSIM_URL ?? 'https://bank.localtest.me';

export default defineConfig({
  testDir: './tests',
  globalSetup: './support/global-setup.ts',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    locale: 'nl-NL',
    timezoneId: 'Europe/Amsterdam',
    // De BankSim-CA is lokaal; zet BANKSIM_VERTROUW_CA=true als hij vertrouwd is (deploy/scripts/trust-ca.sh).
    ignoreHTTPSErrors: process.env.BANKSIM_VERTROUW_CA !== 'true',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    { name: 'setup', testMatch: /.*\.setup\.ts/, use: { ...devices['Desktop Chrome'] } },
    { name: 'chromium', use: { ...devices['Desktop Chrome'] }, dependencies: ['setup'] },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] }, dependencies: ['setup'] },
    { name: 'webkit', use: { ...devices['Desktop Safari'] }, dependencies: ['setup'] },
  ],
});
