import { expect, test } from '@playwright/test';
import { AdminPagina, OverzichtPagina, RekeningPagina } from '../pages/bank';
import { AUTH, KLANT, SIMULATIEDATUM } from '../support/omgeving';

const MAANDEN = ['januari', 'februari', 'maart', 'april', 'mei', 'juni', 'juli', 'augustus', 'september', 'oktober',
  'november', 'december'];

/** "Woensdag 15 juli 2026" → datum. */
function alsDatum(datumregel: string): Date {
  const [, dag, maand, jaar] = datumregel.trim().split(/\s+/);
  return new Date(Date.UTC(parseInt(jaar, 10), MAANDEN.indexOf(maand), parseInt(dag, 10)));
}

test.describe('beheerder', () => {
  test.use({ storageState: AUTH.beheerder });

  test('ziet de rekeninghouders en kan een klant alleen-lezen bekijken', async ({ page }) => {
    const admin = new AdminPagina(page);
    await admin.open();
    await expect(page.getByRole('heading', { name: 'Rekeninghouders (10)' })).toBeVisible();
    await page.getByPlaceholder('Zoek naam').fill('vries');
    await expect(page.locator('tbody tr')).toHaveCount(1);

    await admin.rekeninghouder(KLANT.naam).click();
    await expect(page.getByText(`Je bekijkt de rekeningen van ${KLANT.naam} (alleen lezen).`)).toBeVisible();
    await page.getByRole('link', { name: /Betaalrekening NL/ }).click();
    const rekening = new RekeningPagina(page);
    await expect(rekening.regels().first()).toBeVisible();
    await expect(page.getByRole('link', { name: 'Betalen' })).toHaveCount(0);
    await expect(rekening.zoekKnop()).toBeVisible();

    await page.getByRole('button', { name: '‹ Terug' }).click();
    await page.getByRole('link', { name: /Spaarrekening/ }).click();
    await expect(new RekeningPagina(page).regels().first()).toBeVisible();
    await expect(page.getByRole('link', { name: 'Opnemen' })).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Inleggen' })).toHaveCount(0);
  });
});

test.describe('simulatiedatum', () => {
  test('terugzetten verbergt latere transacties voor de klant; vooruit maakt ze weer zichtbaar', async ({ browser }) => {
    const beheer = await browser.newContext({ storageState: AUTH.beheerder });
    const klant = await browser.newContext({ storageState: AUTH.klant });
    const admin = new AdminPagina(await beheer.newPage());
    const klantPagina = await klant.newPage();
    try {
      await admin.open();
      await admin.zetSimulatiedatum('2026-07-15');

      const overzicht = new OverzichtPagina(klantPagina);
      await expect(async () => {
        await overzicht.open();
        await expect(overzicht.topbar).toContainText('Datum: 15 juli 2026', { timeout: 1_000 });
      }).toPass({ timeout: 20_000 });
      await overzicht.betaalrekening().click();
      const rekening = new RekeningPagina(klantPagina);
      await expect(rekening.regels().first()).toBeVisible();
      // De nieuwste transactie is van uiterlijk 15 juli 2026.
      const eerste = alsDatum(await rekening.datumregels().first().innerText());
      expect(eerste.getTime()).toBeLessThanOrEqual(Date.UTC(2026, 6, 15));
      expect(eerste.getTime()).toBeGreaterThan(Date.UTC(2026, 5, 1));
    } finally {
      await admin.zetSimulatiedatum(SIMULATIEDATUM);
      // Wachten tot elke API-pod de datum weer kent (cache 5 s), zodat volgende tests de beginstand zien.
      const overzicht = new OverzichtPagina(klantPagina);
      await expect(async () => {
        await overzicht.open();
        await expect(overzicht.topbar).toContainText('Datum: 5 oktober 2026', { timeout: 1_000 });
      }).toPass({ timeout: 20_000 });
      await klantPagina.waitForTimeout(6_000);
      await beheer.close();
      await klant.close();
    }
  });
});
