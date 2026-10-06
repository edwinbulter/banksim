import { expect, test } from '@playwright/test';
import { RekeningPagina } from '../pages/bank';
import { ANDERE_KLANT, AUTH } from '../support/omgeving';

test.use({ storageState: AUTH.klant });

/** Autorisatie gebeurt in bank-api; de app laat alleen netjes zien wat er mag (TO §10.3). */
test.describe('toegangscontrole', () => {
  test('klant kan niet bij de beheerfuncties', async ({ page }) => {
    await page.goto('/admin');
    await expect(page).toHaveURL(/bank\.localtest\.me\/$/);
    await expect(page.getByRole('heading', { name: /Welkom/ })).toBeVisible();
    expect((await page.request.get('/api/admin/holders')).status()).toBe(403);
  });

  test('rekening van een ander is niet te zien', async ({ page }) => {
    await page.goto(`/betaalrekening/${ANDERE_KLANT.iban}`);
    await expect(page.getByRole('alert')).toContainText('Rekening is niet gevonden.');
    await expect(new RekeningPagina(page).kop()).toHaveCount(0);
    expect((await page.request.get(`/api/accounts/${ANDERE_KLANT.iban}/transactions`)).status()).toBe(404);
  });

  test('betalen zonder CSRF-token wordt geweigerd', async ({ page }) => {
    await page.goto('/');
    const antwoord = await page.request.post('/api/payments', {
      headers: { 'Idempotency-Key': crypto.randomUUID(), 'Content-Type': 'application/json' },
      data: { vanIban: 'NL13SIMB0000100011', bedrag: '1.00', naamOntvanger: ANDERE_KLANT.naam, ibanOntvanger: ANDERE_KLANT.iban },
    });
    expect(antwoord.status()).toBe(403);
  });
});
