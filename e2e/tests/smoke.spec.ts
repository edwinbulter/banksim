import { expect, test } from '@playwright/test';

test('startpagina is bereikbaar', async ({ page }) => {
  const response = await page.goto('/');
  expect(response?.ok()).toBeTruthy();
});
