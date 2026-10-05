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

  it('zet de ingelogde gebruiker', async () => {
    const laden = service.laad();
    http.expectOne('/api/me').flush({ naam: 'Jan de Vries', gebruikersnaam: 'jdevries', rollen: ['klant'] });
    await laden;
    expect(service.gebruiker()?.naam).toBe('Jan de Vries');
    expect(navigaties).toEqual([]);
  });

  it('stuurt naar de login bij 401', async () => {
    const laden = service.laad();
    http.expectOne('/api/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await laden;
    expect(service.gebruiker()).toBeNull();
    expect(navigaties).toEqual([LOGIN_URL]);
  });

  it('geeft andere fouten door', async () => {
    const laden = service.laad();
    http.expectOne('/api/me').flush(null, { status: 503, statusText: 'Service Unavailable' });
    await expect(laden).rejects.toBeTruthy();
  });
});
