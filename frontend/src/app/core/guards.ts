import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionService } from './session.service';

/** Klantschermen: de beheerder gaat naar zijn eigen startpagina (alleen UX; bank-api beslist). */
export const klantGuard: CanActivateFn = async () => {
  const sessie = inject(SessionService);
  const router = inject(Router);
  const gebruiker = await sessie.laad();
  if (!gebruiker) {
    return false;
  }
  return sessie.isAdmin() ? router.parseUrl('/admin') : true;
};

/** Admin-schermen alleen voor de beheerder. */
export const adminGuard: CanActivateFn = async () => {
  const sessie = inject(SessionService);
  const router = inject(Router);
  const gebruiker = await sessie.laad();
  if (!gebruiker) {
    return false;
  }
  return sessie.isAdmin() ? true : router.parseUrl('/');
};

/** Rekeningschermen: klant (eigen rekeningen) en beheerder (alleen-lezen). */
export const ingelogdGuard: CanActivateFn = async () => (await inject(SessionService).laad()) !== null;
