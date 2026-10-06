import { expect, test } from '@playwright/test';
import { OverzichtPagina, RekeningPagina } from '../pages/bank';
import { AUTH, centen } from '../support/omgeving';

test.use({ storageState: AUTH.klant });

test.describe('spaarrekening', () => {
  let spaar: RekeningPagina;

  test.beforeEach(async ({ page }) => {
    const overzicht = new OverzichtPagina(page);
    await overzicht.open();
    await overzicht.spaarrekening().click();
    spaar = new RekeningPagina(page);
    await expect(spaar.regels().first()).toBeVisible();
  });

  test('kop met lopende rente, kopregel, jaartalregel en spaardetails', async ({ page }) => {
    await expect(spaar.kop()).toContainText('Spaarrekening');
    await expect(spaar.kop()).toContainText(/Rente 3,00%, deze maand opgebouwd.*€ \d/);
    await expect(page.getByRole('heading', { name: 'Af- en bijschrijvingen' })).toBeVisible();
    await expect(spaar.jaarregels().first()).toHaveText('2025');

    await spaar.regel(/Rente/).first().click();
    const details = spaar.details().first();
    await expect(details).toContainText('Datum');
    await expect(details).toContainText(/Tegenrekening\s*BankSim Rente/);
    await expect(details).toContainText(/Type\s*Rente/);
  });

  test('Inleggen en Opnemen via het Overschrijven-scherm', async ({ page }) => {
    const spaarVoor = centen(await spaar.saldo().innerText());

    await page.getByRole('link', { name: 'Inleggen' }).click();
    await expect(page.getByRole('heading', { name: 'Overschrijven' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Van' })).toContainText('Betaalrekening');
    await expect(page.getByRole('region', { name: 'Naar' })).toContainText('Spaarrekening');
    await page.getByLabel('Bedrag (€)').fill('20');
    await page.getByRole('button', { name: 'Overschrijven' }).click();
    await expect(spaar.saldo()).toBeVisible();
    expect(centen(await spaar.saldo().innerText())).toBe(spaarVoor + 2000);
    await expect(spaar.regels().first()).toContainText('+20,00');

    await page.getByRole('link', { name: 'Opnemen' }).click();
    await expect(page.getByRole('region', { name: 'Van' })).toContainText('Spaarrekening');
    await page.getByLabel('Bedrag (€)').fill('5');
    await page.getByLabel('Omschrijving', { exact: true }).fill('Terug naar betaal');
    await page.getByRole('button', { name: 'Overschrijven' }).click();
    await expect(spaar.regels().first()).toContainText('Terug naar betaal');
    expect(centen(await spaar.saldo().innerText())).toBe(spaarVoor + 1500);
  });

  test('wisselknop draait Van en Naar om', async ({ page }) => {
    await page.getByRole('link', { name: 'Opnemen' }).click();
    await expect(page.getByRole('region', { name: 'Van' })).toContainText('Spaarrekening');
    await page.getByRole('button', { name: 'Van en naar wisselen' }).click();
    await expect(page.getByRole('region', { name: 'Van' })).toContainText('Betaalrekening');
  });
});
