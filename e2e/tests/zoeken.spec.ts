import { expect, test } from '@playwright/test';
import { OverzichtPagina, RekeningPagina } from '../pages/bank';
import { AUTH, centen } from '../support/omgeving';

test.use({ storageState: AUTH.klant });

test.describe('zoeken in transacties', () => {
  let rekening: RekeningPagina;

  test.beforeEach(async ({ page }) => {
    const overzicht = new OverzichtPagina(page);
    await overzicht.open();
    await overzicht.betaalrekening().click();
    rekening = new RekeningPagina(page);
    await expect(rekening.regels().first()).toBeVisible();
  });

  test('de knop wordt "Verberg zoeken" en klapt het formulier in en uit', async ({ page }) => {
    await expect(rekening.zoekKnop()).toHaveText('Zoeken in transacties');
    await rekening.zoekKnop().click();
    await expect(rekening.zoekKnop()).toHaveText('Verberg zoeken');
    await expect(page.getByRole('form', { name: 'Zoeken in transacties' })).toBeVisible();
    await expect(page.getByLabel('Transactietype')).toContainText('Alle transactietypes');
    await expect(page.getByLabel('Alle transacties')).toBeChecked();
    await rekening.zoekKnop().click();
    await expect(page.getByRole('form', { name: 'Zoeken in transacties' })).toBeHidden();
  });

  test('op naam en bedrag', async () => {
    await rekening.zoekKnop().click();
    await rekening.zoek({ tekst: 'BuurtSuper', min: '50', max: '150' });
    await expect(rekening.regels().first()).toBeVisible();
    for (const regel of await rekening.regels().all()) {
      await expect(regel).toContainText('BuurtSuper');
      const bedrag = Math.abs(centen(await regel.locator('.bedrag').innerText()));
      expect(bedrag).toBeGreaterThanOrEqual(5000);
      expect(bedrag).toBeLessThanOrEqual(15000);
    }
  });

  test('alleen inkomend en alleen uitgaand', async () => {
    await rekening.zoekKnop().click();
    await rekening.zoek({ richting: 'Inkomende transacties' });
    await expect(rekening.regels().first()).toBeVisible();
    await expect(rekening.regels().filter({ hasText: '−' })).toHaveCount(0);
    await rekening.zoek({ richting: 'Uitgaande transacties' });
    await expect(rekening.regels().first()).toBeVisible();
    await expect(rekening.regels().filter({ hasText: '+' })).toHaveCount(0);
  });

  test('ongeldig bedragbereik geeft een melding', async ({ page }) => {
    await rekening.zoekKnop().click();
    await rekening.zoek({ min: '50', max: '20' });
    await expect(page.getByRole('alert')).toHaveText('"Bedrag van" mag niet groter zijn dan "Bedrag t/m".');
  });

  test('Wissen maakt de velden leeg en toont weer alles', async ({ page }) => {
    await rekening.zoekKnop().click();
    await rekening.zoek({ tekst: 'onbestaande-naam-xyz' });
    await expect(page.getByText('Geen transacties gevonden.')).toBeVisible();
    await page.getByRole('button', { name: 'Wissen' }).click();
    await expect(page.getByLabel('Naam, bedrag, IBAN of omschrijving')).toHaveValue('');
    await expect(rekening.regels()).toHaveCount(50);
  });
});
