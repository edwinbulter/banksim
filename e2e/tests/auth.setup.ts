import { expect, test as setup } from '@playwright/test';
import { LoginPagina } from '../pages/login';
import { AUTH, BEHEERDER, KLANT, beheerderWachtwoord, klantWachtwoord } from '../support/omgeving';

/** Eén keer inloggen per run; de sessiecookies gebruiken alle browsers. */
setup('inloggen als klant', async ({ page }) => {
  const login = new LoginPagina(page);
  await login.open();
  await login.inloggen(KLANT.gebruiker, klantWachtwoord());
  await expect(page.getByRole('heading', { name: `Welkom, ${KLANT.naam}` })).toBeVisible();
  await page.context().storageState({ path: AUTH.klant });
});

setup('inloggen als beheerder', async ({ page }) => {
  const login = new LoginPagina(page);
  await login.open();
  await login.inloggen(BEHEERDER.gebruiker, beheerderWachtwoord());
  await expect(page).toHaveURL(/\/admin$/);
  await page.context().storageState({ path: AUTH.beheerder });
});
