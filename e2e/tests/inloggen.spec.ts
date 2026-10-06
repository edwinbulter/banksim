import { expect, test } from '@playwright/test';
import { LoginPagina } from '../pages/login';
import { OverzichtPagina } from '../pages/bank';
import { BEHEERDER, KLANT, KLANT_FOUT_WACHTWOORD, beheerderWachtwoord, klantWachtwoord } from '../support/omgeving';

/** Inloggen en uitloggen via Keycloak (TO §14), steeds met een verse browsercontext. */
test.describe('inloggen', () => {
  test('klant komt op het Overzicht met betaal- en spaarrekening', async ({ page }) => {
    const login = new LoginPagina(page);
    await login.open();
    await login.inloggen(KLANT.gebruiker, klantWachtwoord());

    const overzicht = new OverzichtPagina(page);
    await expect(page).toHaveURL(/bank\.localtest\.me\/$/);
    await expect(page.getByRole('heading', { name: `Welkom, ${KLANT.naam}` })).toBeVisible();
    await expect(overzicht.betaalrekening()).toContainText(/€ [\d.]+,\d\d/);
    await expect(overzicht.spaarrekening()).toContainText(/€ [\d.]+,\d\d/);
    await expect(overzicht.topbar).toContainText('Datum: 5 oktober 2026');
  });

  test('beheerder komt op het Admin-scherm', async ({ page }) => {
    const login = new LoginPagina(page);
    await login.open();
    await login.inloggen(BEHEERDER.gebruiker, beheerderWachtwoord());
    await expect(page).toHaveURL(/\/admin$/);
    await expect(page.getByRole('heading', { name: 'Beheer' })).toBeVisible();
  });

  test('verkeerd wachtwoord geeft een foutmelding', async ({ page }) => {
    const login = new LoginPagina(page);
    await login.open();
    await login.inloggen(KLANT_FOUT_WACHTWOORD.gebruiker, 'helemaal-fout-wachtwoord');
    await expect(await login.foutmelding()).toBeVisible();
    await expect(page).toHaveURL(/auth\.localtest\.me/);
  });

  test('uitloggen beëindigt de sessie', async ({ page }) => {
    const login = new LoginPagina(page);
    await login.open();
    await login.inloggen(KLANT.gebruiker, klantWachtwoord());
    await expect(page.getByRole('heading', { name: /Welkom/ })).toBeVisible();

    await new OverzichtPagina(page).uitloggen();
    await expect(page).toHaveURL(/auth\.localtest\.me/);

    // Terug of opnieuw naar de bank: geen gegevens meer, wel weer het inlogscherm.
    await page.goto('/');
    await expect(page).toHaveURL(/auth\.localtest\.me/);
    expect((await page.request.get('/api/me')).status()).toBe(401);
  });
});
