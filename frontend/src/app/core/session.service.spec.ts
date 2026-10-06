import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BROWSER_NAVIGATIE, LOGIN_URL, SessionService } from './session.service';

describe('SessionService', () => {
  let service: SessionService;
  let http: HttpTestingController;
  let navigaties: string[];

  beforeEach(() => {
    navigaties = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BROWSER_NAVIGATIE, useValue: (url: string) => navigaties.push(url) },
      ],
    });
    service = TestBed.inject(SessionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('zet de ingelogde gebruiker en herkent de beheerder', async () => {
    const laden = service.laad();
    http.expectOne('/api/me').flush({ naam: 'BankSim Beheerder', gebruikersnaam: 'beheerder', rollen: ['admin'] });
    await laden;
    expect(service.gebruiker()?.naam).toBe('BankSim Beheerder');
    expect(service.isAdmin()).toBe(true);
  });

  it('laadt maar één keer', async () => {
    const a = service.laad();
    const b = service.laad();
    http.expectOne('/api/me').flush({ naam: 'Jan', gebruikersnaam: 'jdevries', rollen: ['klant'] });
    expect(await a).toEqual(await b);
    expect(service.isAdmin()).toBe(false);
  });

  it('stuurt naar de login bij 401', async () => {
    const laden = service.laad();
    http.expectOne('/api/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(await laden).toBeNull();
    expect(navigaties).toEqual([LOGIN_URL]);
  });

  it('geeft andere fouten door', async () => {
    const laden = service.laad();
    http.expectOne('/api/me').flush(null, { status: 503, statusText: 'Service Unavailable' });
    await expect(laden).rejects.toBeTruthy();
  });
});
