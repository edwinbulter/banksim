import { HttpErrorResponse } from '@angular/common/http';
import { foutmelding } from './fout';

describe('foutmelding', () => {
  it('neemt de tekst van de API over', () => {
    const fout = new HttpErrorResponse({
      status: 422,
      error: { type: 'https://banksim.local/problems/saldo-ontoereikend', detail: 'Saldo ontoereikend.' },
    });
    expect(foutmelding(fout)).toEqual({ tekst: 'Saldo ontoereikend.', tijdelijk: false, code: 'saldo-ontoereikend' });
  });

  it('herkent tijdelijke fouten', () => {
    expect(foutmelding(new HttpErrorResponse({ status: 0 })).tijdelijk).toBe(true);
    expect(foutmelding(new HttpErrorResponse({ status: 503 })).tijdelijk).toBe(true);
    expect(foutmelding(new HttpErrorResponse({ status: 429, error: { detail: 'Wacht even.' } }))).toMatchObject({ tijdelijk: true, tekst: 'Wacht even.' });
    expect(foutmelding(new Error('x')).tijdelijk).toBe(false);
  });
});
