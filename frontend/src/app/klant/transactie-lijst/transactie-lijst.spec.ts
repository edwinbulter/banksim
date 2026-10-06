import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideApi, RekeningSoort, Transactie } from '../../api';
import { TransactieLijst } from './transactie-lijst';

function transactie(id: string, datum: string, overrides: Partial<Transactie> = {}): Transactie {
  return {
    id, datum, tijdstip: `${datum}T11:26:00Z`, uitgevoerdOp: datum, tegenNaam: 'Eneco', tegenIban: 'NL61SIMB0000009001',
    bedrag: '-148.00', type: 'INCASSO', typeLabel: 'Incasso', rekeninghouder: 'Jan de Vries',
    van: { naam: 'Jan de Vries', iban: 'NL13SIMB0000100011' }, naar: { naam: 'Eneco', iban: 'NL61SIMB0000009001' },
    omschrijving: 'Termijn', ...overrides,
  } as Transactie;
}

describe('TransactieLijst', () => {
  let fixture: ComponentFixture<TransactieLijst>;
  let http: HttpTestingController;

  async function maak(soort: RekeningSoort): Promise<void> {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideApi('')] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TransactieLijst);
    fixture.componentRef.setInput('iban', 'NL13SIMB0000100011');
    fixture.componentRef.setInput('soort', soort);
    fixture.detectChanges();
  }

  const tekst = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => http.verify());

  it('toont datumregels, regels met bedrag en laadt met "Toon meer" de volgende pagina', async () => {
    await maak(RekeningSoort.BETAAL);
    http.expectOne((r) => r.url === '/api/accounts/NL13SIMB0000100011/transactions' && !r.params.has('cursor'))
      .flush({ items: [transactie('1', '2026-10-02'), transactie('2', '2026-10-01', { bedrag: '3215.00', tegenNaam: 'Werkgever' })], nextCursor: 'c1' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(tekst()).toContain('Vrijdag 2 oktober 2026');
    expect(tekst()).toContain('Donderdag 1 oktober 2026');
    expect(tekst()).toContain('−148,00');
    expect(tekst()).toContain('+3.215,00');

    const knop = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) => b.textContent?.includes('Toon meer')) as HTMLButtonElement;
    knop.click();
    http.expectOne((r) => r.params.get('cursor') === 'c1').flush({ items: [transactie('3', '2026-09-30')] });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(tekst()).toContain('Woensdag 30 september 2026');
    expect(tekst()).not.toContain('Toon meer');
  });

  it('klapt een regel uit met de details uit het FO', async () => {
    await maak(RekeningSoort.BETAAL);
    http.expectOne(() => true).flush({ items: [transactie('1', '2026-10-03')] });
    await fixture.whenStable();
    fixture.detectChanges();

    const regel = fixture.nativeElement.querySelector('.transactie__regel') as HTMLButtonElement;
    expect(regel.getAttribute('aria-expanded')).toBe('false');
    regel.click();
    fixture.detectChanges();
    expect(regel.getAttribute('aria-expanded')).toBe('true');
    expect(tekst()).toContain('Naam rekeninghouder');
    expect(tekst()).toContain('Transactietype');
    expect(tekst()).toContain('NL61 SIMB 0000 0090 01');
    expect(tekst()).toContain('3 oktober 2026 om 13:26');
    expect(tekst()).toContain('Uitgevoerd op');
  });

  it('spaarrekening: jaartalregel, omschrijving in de regel en spaardetails', async () => {
    await maak(RekeningSoort.SPAAR);
    http.expectOne(() => true).flush({ items: [
      transactie('1', '2026-01-03', { omschrijving: 'Inleg', typeLabel: 'Inleg', bedrag: '250.00' }),
      transactie('2', '2025-12-31', { omschrijving: 'Rente december 2025', typeLabel: 'Rente', tegenNaam: 'BankSim Rente', bedrag: '30.12' }),
    ] });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.jaarregel')?.textContent).toContain('2025');
    expect(tekst()).toContain('Rente december 2025');
    (fixture.nativeElement.querySelectorAll('.transactie__regel')[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(tekst()).toContain('Tegenrekening');
    expect(tekst()).toContain('BankSim Rente');
    expect(tekst()).not.toContain('Naam rekeninghouder');
  });

  it('toont een foutmelding met "Opnieuw proberen" als de bank even weg is', async () => {
    await maak(RekeningSoort.BETAAL);
    http.expectOne(() => true).flush({ detail: 'De bank is even niet bereikbaar.' }, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(tekst()).toContain('De bank is even niet bereikbaar.');
    const opnieuw = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) => b.textContent?.includes('Opnieuw')) as HTMLButtonElement;
    opnieuw.click();
    http.expectOne(() => true).flush({ items: [] });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(tekst()).toContain('Nog geen af- of bijschrijvingen.');
  });
});
