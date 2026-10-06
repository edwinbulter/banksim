import { Locator, Page, expect } from '@playwright/test';

/** Gedeelde onderdelen van de bank-app. */
export class Bank {
  readonly topbar: Locator;

  constructor(readonly page: Page) {
    this.topbar = page.locator('.topbar');
  }

  async uitloggen(): Promise<void> {
    await this.page.getByRole('button', { name: 'Uitloggen' }).click();
  }

  foutmelding(): Locator {
    return this.page.getByRole('alert');
  }
}

/** Overzichtscherm (FO). */
export class OverzichtPagina extends Bank {
  async open(): Promise<void> {
    await this.page.goto('/');
    await expect(this.page.getByRole('heading', { name: /Welkom/ })).toBeVisible();
  }

  betaalrekening(): Locator {
    return this.page.getByRole('link', { name: /Betaalrekening NL/ });
  }

  spaarrekening(): Locator {
    return this.page.getByRole('link', { name: /Spaarrekening/ });
  }
}

/** Betaalrekening- en Spaarrekening-scherm: kop en transactielijst. */
export class RekeningPagina extends Bank {
  async open(iban: string, soort: 'betaalrekening' | 'spaarrekening' = 'betaalrekening'): Promise<void> {
    await this.page.goto(`/${soort}/${iban}`);
    await expect(this.saldo()).toBeVisible();
  }

  kop(): Locator {
    return this.page.locator('.rekening-kop');
  }

  saldo(): Locator {
    return this.page.locator('.rekening-kop__saldo .bedrag');
  }

  datumregels(): Locator {
    return this.page.locator('.datumregel');
  }

  jaarregels(): Locator {
    return this.page.locator('.jaarregel');
  }

  regels(): Locator {
    return this.page.locator('.transactie__regel');
  }

  regel(naam: string | RegExp): Locator {
    return this.regels().filter({ hasText: naam });
  }

  details(): Locator {
    return this.page.locator('.details');
  }

  toonMeer(): Locator {
    return this.page.getByRole('button', { name: 'Toon meer' });
  }

  zoekKnop(): Locator {
    return this.page.getByRole('button', { name: /Zoeken in transacties|Verberg zoeken/ });
  }

  async zoek(velden: { tekst?: string; min?: string; max?: string; type?: string; richting?: string }): Promise<void> {
    if (velden.tekst !== undefined) await this.page.getByLabel('Naam, bedrag, IBAN of omschrijving').fill(velden.tekst);
    if (velden.min !== undefined) await this.page.getByLabel('Bedrag van (€)').fill(velden.min);
    if (velden.max !== undefined) await this.page.getByLabel('Bedrag t/m (€)').fill(velden.max);
    if (velden.type !== undefined) await this.page.getByLabel('Transactietype').selectOption({ label: velden.type });
    if (velden.richting !== undefined) await this.page.getByLabel(velden.richting).check();
    await this.page.getByRole('button', { name: 'Zoeken', exact: true }).click();
  }
}

/** Betalen-scherm (FO). */
export class BetalenPagina extends Bank {
  async vulIn(velden: { bedrag: string; naam: string; iban?: string; omschrijving?: string }): Promise<void> {
    await this.page.getByLabel('Bedrag (€)').fill(velden.bedrag);
    await this.page.getByLabel('Naam ontvanger').fill(velden.naam);
    if (velden.iban !== undefined) {
      await this.page.getByLabel('Rekeningnummer (IBAN)').fill(velden.iban);
    }
    if (velden.omschrijving !== undefined) {
      await this.page.getByLabel('Omschrijving', { exact: true }).fill(velden.omschrijving);
    }
  }

  suggestie(naam: string): Locator {
    return this.page.getByRole('list', { name: 'Suggesties uit je contacten' }).getByRole('button', { name: new RegExp(naam) });
  }

  async volgende(): Promise<void> {
    await this.page.getByRole('button', { name: 'Volgende' }).click();
  }

  async bevestigen(): Promise<void> {
    await this.page.getByRole('button', { name: 'Bevestigen' }).click();
  }
}

/** Admin-scherm (FO). */
export class AdminPagina extends Bank {
  async open(): Promise<void> {
    await this.page.goto('/admin');
    await expect(this.page.getByRole('heading', { name: 'Beheer' })).toBeVisible();
  }

  async zetSimulatiedatum(datum: string): Promise<void> {
    await this.page.getByRole('textbox', { name: 'Datum' }).fill(datum);
    await this.page.getByRole('button', { name: 'Toepassen' }).click();
    await expect(this.page.getByRole('status')).toContainText('De simulatiedatum is nu');
  }

  rekeninghouder(naam: string): Locator {
    return this.page.getByRole('link', { name: naam });
  }
}
