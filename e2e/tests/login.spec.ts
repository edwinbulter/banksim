import { expect, test } from '@playwright/test';

const wachtwoord = process.env.BANKSIM_DEMO_PASSWORD;

test.describe('inloggen', () => {
  test.skip(!wachtwoord, 'BANKSIM_DEMO_PASSWORD is niet gezet');

  test('demo-klant logt in via Keycloak en ziet zijn naam', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/auth\.localtest\.me\/realms\/banksim/);

    await page.getByLabel(/gebruikersnaam|username/i).fill('demo');
    await page.getByLabel(/wachtwoord|password/i, { exact: false }).first().fill(wachtwoord!);
    await page.getByRole('button', { name: /inloggen|sign in|log in/i }).click();

    await expect(page).toHaveURL('https://bank.localtest.me/');
    await expect(page.getByText('Welkom, Demo Klant')).toBeVisible();
  });
});
