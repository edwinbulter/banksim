import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideApi } from '../../api';
import { Betalen } from './betalen';

describe('Betalen', () => {
  let fixture: ComponentFixture<Betalen>;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApi(''), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Betalen);
    fixture.componentRef.setInput('iban', 'NL13SIMB0000100011');
    fixture.detectChanges();
    http.expectOne('/api/accounts/NL13SIMB0000100011').flush({
      iban: 'NL13SIMB0000100011', soort: 'BETAAL', rekeninghouder: 'Jan de Vries', saldo: '1899.10',
      simulatiedatum: '2026-10-06', alleenLezen: false,
    });
    await fixture.whenStable();
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const element = () => fixture.nativeElement as HTMLElement;
  /** Het invoerveld bij een label, via for/id (zoals een schermlezer het koppelt). */
  const veld = (label: string) => {
    const element_ = [...element().querySelectorAll('label')].find((l) => l.textContent?.includes(label))!;
    return element().querySelector(`#${element_.htmlFor}`) as HTMLInputElement;
  };
  const typ = (label: string, waarde: string) => {
    veld(label).value = waarde;
    veld(label).dispatchEvent(new Event('input'));
  };
  const knop = (tekst: string) =>
    [...element().querySelectorAll('button')].find((b) => b.textContent?.trim().startsWith(tekst)) as HTMLButtonElement;

  function vulIn(): void {
    typ('Bedrag', '12,34');
    typ('Naam ontvanger', 'Sanne Bakker');
    typ('Rekeningnummer', 'NL34 SIMB 0000 1000 21');
    typ('Omschrijving', 'Etentje');
    knop('Volgende').click();
    fixture.detectChanges();
  }

  it('toont de van-rekening en eist een geldig bedrag', () => {
    expect(element().textContent).toContain('Jan de Vries');
    expect(element().textContent).toContain('NL13 SIMB 0000 1000 11');
    typ('Bedrag', '1,001');
    knop('Volgende').click();
    fixture.detectChanges();
    expect(element().textContent).toContain('Vul een bedrag groter dan € 0,00 in');
    expect(element().textContent).not.toContain('Controleer je betaling');
  });

  it('controlestap en betaling met één Idempotency-Key, ook bij opnieuw proberen', async () => {
    vulIn();
    expect(element().textContent).toContain('Controleer je betaling');
    expect(element().textContent).toContain('€ 12,34');

    knop('Bevestigen').click();
    const eerste = http.expectOne('/api/payments');
    expect(eerste.request.body).toEqual({
      vanIban: 'NL13SIMB0000100011', bedrag: '12.34', naamOntvanger: 'Sanne Bakker', ibanOntvanger: 'NL34SIMB0000100021',
      omschrijving: 'Etentje',
    });
    const sleutel = eerste.request.headers.get('Idempotency-Key');
    expect(sleutel).toMatch(/^[0-9a-f-]{36}$/);
    eerste.flush({ detail: 'De bank is even niet bereikbaar.' }, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(element().textContent).toContain('De bank is even niet bereikbaar.');

    const navigatie = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    knop('Opnieuw proberen').click();
    const tweede = http.expectOne('/api/payments');
    expect(tweede.request.headers.get('Idempotency-Key')).toBe(sleutel);
    tweede.flush({ overboekingId: 'x', datum: '2026-10-06', bedrag: '12.34', saldo: '1886.76' }, { status: 201, statusText: 'Created' });
    await fixture.whenStable();
    expect(navigatie).toHaveBeenCalledWith(['/betaalrekening', 'NL13SIMB0000100011'], expect.anything());
  });

  it('na een afwijzing hoort bij een nieuwe poging een nieuwe sleutel', async () => {
    vulIn();
    knop('Bevestigen').click();
    const eerste = http.expectOne('/api/payments');
    const sleutel = eerste.request.headers.get('Idempotency-Key');
    eerste.flush({ type: 'https://banksim.local/problems/saldo-ontoereikend', detail: 'Saldo ontoereikend.' },
      { status: 422, statusText: 'Unprocessable' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(element().textContent).toContain('Saldo ontoereikend.');
    knop('Bevestigen').click();
    expect(http.expectOne('/api/payments').request.headers.get('Idempotency-Key')).not.toBe(sleutel);
  });
});
