import { expect, test } from '@playwright/test';
import { BetalenPagina, OverzichtPagina, RekeningPagina } from '../pages/bank';
import { LoginPagina } from '../pages/login';
import { ANDERE_KLANT, AUTH, KLANT, centen, klantWachtwoord } from '../support/omgeving';

test.use({ storageState: AUTH.klant });

/** Bedrag per browser verschillend, zodat de betaling van deze run in de lijst te herkennen is. */
function bedragVoor(browser: string): { invoer: string; tekst: string; centen: number } {
  const c = { chromium: 111, firefox: 122, webkit: 133 }[browser] ?? 144;
  return { invoer: `${Math.floor(c / 100)},${String(c % 100).padStart(2, '0')}`, tekst: `${Math.floor(c / 100)},${String(c % 100).padStart(2, '0')}`, centen: c };
}

test.describe('betalen', () => {
  let rekening: RekeningPagina;
  let betalen: BetalenPagina;

  test.beforeEach(async ({ page }) => {
    const overzicht = new OverzichtPagina(page);
    await overzicht.open();
    await overzicht.betaalrekening().click();
    rekening = new RekeningPagina(page);
    await expect(rekening.saldo()).toBeVisible();
    betalen = new BetalenPagina(page);
  });

  test('velden met de maximale lengtes uit het FO', async ({ page }) => {
    await page.getByRole('link', { name: 'Betalen' }).click();
    await expect(page.getByRole('heading', { name: 'Betalen' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Van' })).toContainText(KLANT.naam);
    await expect(page.getByLabel('Naam ontvanger')).toHaveAttribute('maxlength', '70');
    await expect(page.getByLabel('Rekeningnummer (IBAN)')).toHaveAttribute('maxlength', '34');
    await expect(page.getByLabel('Omschrijving', { exact: true })).toHaveAttribute('maxlength', '140');
    await expect(page.getByLabel('Betalingskenmerk')).toHaveAttribute('maxlength', '25');
    await expect(page.getByLabel('Extra omschrijving')).toHaveAttribute('maxlength', '35');
  });

  test('betaling naar een contact: suggestie, controle, bevestigen en lager saldo', async ({ page, browserName }) => {
    const bedrag = bedragVoor(browserName);
    const saldoVoor = centen(await rekening.saldo().innerText());
    await page.getByRole('link', { name: 'Betalen' }).click();

    await betalen.vulIn({ bedrag: bedrag.invoer, naam: 'Sanne' });
    await betalen.suggestie(ANDERE_KLANT.naam).click();
    await expect(page.getByLabel('Rekeningnummer (IBAN)')).toHaveValue('NL34 SIMB 0000 1000 21');
    await page.getByLabel('Omschrijving', { exact: true }).fill(`E2E ${browserName}`);
    await betalen.volgende();

    await expect(page.getByRole('heading', { name: 'Controleer je betaling' })).toBeVisible();
    await expect(page.locator('.controle')).toContainText(`€ ${bedrag.tekst}`);
    await expect(page.locator('.controle')).toContainText(ANDERE_KLANT.naam);
    await betalen.bevestigen();

    await expect(page.getByRole('status')).toContainText(`Betaling van € ${bedrag.tekst} aan ${ANDERE_KLANT.naam} is uitgevoerd.`);
    await expect(rekening.regel(ANDERE_KLANT.naam).first()).toContainText(`−${bedrag.tekst}`);
    expect(centen(await rekening.saldo().innerText())).toBe(saldoVoor - bedrag.centen);
  });

  test('de ontvanger ziet de bijschrijving', async ({ browser, browserName }) => {
    const bedrag = bedragVoor(browserName);
    const context = await browser.newContext({ storageState: { cookies: [], origins: [] } });
    const page = await context.newPage();
    const login = new LoginPagina(page);
    await login.open();
    await login.inloggen(ANDERE_KLANT.gebruiker, klantWachtwoord());
    const overzicht = new OverzichtPagina(page);
    await overzicht.betaalrekening().click();
    const ontvanger = new RekeningPagina(page);
    await ontvanger.zoekKnop().click();
    await ontvanger.zoek({ tekst: `E2E ${browserName}` });
    await expect(ontvanger.regel(KLANT.naam).first()).toContainText(`+${bedrag.tekst}`);
    await context.close();
  });

  test('onbekend rekeningnummer wordt geweigerd', async ({ page }) => {
    await page.getByRole('link', { name: 'Betalen' }).click();
    await betalen.vulIn({ bedrag: '5,00', naam: 'Iemand Anders', iban: 'NL91ABNA0417164300' });
    await betalen.volgende();
    await betalen.bevestigen();
    await expect(page.getByRole('alert')).toContainText('Naar dit rekeningnummer kan niet worden betaald.');
  });

  test('meer dan het saldo betalen kan niet (nooit rood)', async ({ page }) => {
    await page.getByRole('link', { name: 'Betalen' }).click();
    await betalen.vulIn({ bedrag: '99999,00', naam: ANDERE_KLANT.naam, iban: ANDERE_KLANT.iban });
    await expect(page.getByText('Dit is meer dan je saldo.')).toBeVisible();
    await betalen.volgende();
    await betalen.bevestigen();
    await expect(page.getByRole('alert')).toContainText('saldo onder € 0,00');
  });

  test('ongeldig bedrag wordt in het formulier al tegengehouden', async ({ page }) => {
    await page.getByRole('link', { name: 'Betalen' }).click();
    await betalen.vulIn({ bedrag: '1,234', naam: ANDERE_KLANT.naam, iban: ANDERE_KLANT.iban });
    await betalen.volgende();
    await expect(page.getByText('Vul een bedrag groter dan € 0,00 in')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Controleer je betaling' })).toBeHidden();
  });
});
