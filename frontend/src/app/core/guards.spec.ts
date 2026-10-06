import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { adminGuard, ingelogdGuard, klantGuard } from './guards';
import { SessionService } from './session.service';

describe('guards', () => {
  function met(gedrag: () => Promise<unknown>, admin = false) {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: SessionService, useValue: { laad: gedrag, isAdmin: () => admin } }],
    });
  }
  const voer = (guard: typeof klantGuard) =>
    TestBed.runInInjectionContext(() => guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));

  it('stuurt de beheerder van klantschermen naar /admin', async () => {
    met(async () => ({ rollen: ['admin'] }), true);
    expect(((await voer(klantGuard)) as UrlTree).toString()).toBe('/admin');
  });

  it('stuurt een klant van /admin naar /', async () => {
    met(async () => ({ rollen: ['klant'] }));
    expect(((await voer(adminGuard)) as UrlTree).toString()).toBe('/');
  });

  it('niet ingelogd: niet doorgaan (de sessie stuurt naar de login)', async () => {
    met(async () => null);
    expect(await voer(ingelogdGuard)).toBe(false);
  });

  it('bij een storing laadt de pagina toch, zodat die zelf een melding toont', async () => {
    met(async () => { throw new Error('503'); });
    expect(await voer(klantGuard)).toBe(true);
    expect(await voer(ingelogdGuard)).toBe(true);
  });
});
