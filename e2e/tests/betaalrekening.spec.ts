import { expect, test } from '@playwright/test';
import { OverzichtPagina, RekeningPagina } from '../pages/bank';
import { AUTH, KLANT } from '../support/omgeving';

test.use({ storageState: AUTH.klant });

test.describe('betaalrekening', () => {
  let rekening: RekeningPagina;

  test.beforeEach(async ({ page }) => {
    const overzicht = new OverzichtPagina(page);
    await overzicht.open();
    await overzicht.betaalrekening().click();
    rekening = new RekeningPagina(page);
    await expect(rekening.regels().first()).toBeVisible();
  });

  test('kop met naam, IBAN en saldo; datumregels en bedragen met teken', async () => {
    await expect(rekening.kop()).toContainText(KLANT.naam);
    await expect(rekening.kop()).toContainText(/NL\d\d SIMB \d{4} \d{4} \d\d/);
    await expect(rekening.saldo()).toHaveText(/€ [\d.]+,\d\d/);
    await expect(rekening.datumregels().first()).toHaveText(/^(Maandag|Dinsdag|Woensdag|Donderdag|Vrijdag|Zaterdag|Zondag) \d{1,2} \w+ 20\d\d$/);
    await expect(rekening.datumregels().first()).toContainText('oktober 2026');
    await expect(rekening.regels().filter({ hasText: '−' }).first()).toBeVisible();
    await expect(rekening.regels().filter({ hasText: '+' }).first()).toBeVisible();
  });

  test('geen transacties na de simulatiedatum', async () => {
    const eerste = await rekening.datumregels().first().textContent();
    expect(eerste).toMatch(/[1-5] oktober 2026|september 2026/);
  });

  test('uitklappen toont de details uit het FO', async () => {
    await rekening.zoekKnop().click();
    await rekening.zoek({ type: 'Incasso' });
    const regel = rekening.regels().first();
    await regel.click();
    await expect(regel).toHaveAttribute('aria-expanded', 'true');
    const details = rekening.details().first();
    await expect(details).toContainText('Naam rekeninghouder');
    await expect(details).toContainText(KLANT.naam);
    await expect(details).toContainText('Transactietype');
    await expect(details).toContainText('Incasso');
    await expect(details).toContainText(/Van\s*Jan de Vries\s*NL\d\d SIMB/);
    await expect(details).toContainText(/Naar\s*.+\s*NL\d\d SIMB/);
    await expect(details).toContainText(/Datum transactie\s*\d{1,2} \w+ 20\d\d om \d\d:\d\d/);
    await expect(details).toContainText(/Uitgevoerd op\s*\d{1,2} \w+ 20\d\d/);
    await regel.click();
    await expect(regel).toHaveAttribute('aria-expanded', 'false');
  });

  test('betaalautomaat heeft geen IBAN bij Naar', async () => {
    await rekening.zoekKnop().click();
    await rekening.zoek({ type: 'Betaalautomaat' });
    await rekening.regels().first().click();
    const details = rekening.details().first();
    await expect(details).toContainText('Betaalautomaat');
    const naar = details.locator('dt', { hasText: 'Naar' }).locator('xpath=following-sibling::dd[1]');
    await expect(naar).not.toContainText('SIMB');
  });

  test('Toon meer laadt de volgende 50 en verdwijnt aan het eind', async () => {
    await rekening.zoekKnop().click();
    await rekening.zoek({ type: 'Verzamelbetaling' });
    await expect(rekening.regels()).toHaveCount(50);
    await rekening.toonMeer().click();
    await expect.poll(async () => rekening.regels().count()).toBeGreaterThan(50);
    await expect(rekening.toonMeer()).toBeHidden();
  });
});
