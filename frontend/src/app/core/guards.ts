import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Gebruiker } from '../api';
import { SessionService } from './session.service';

/**
 * Wie is er ingelogd? Bij een tijdelijke storing (bank-api of BFF even weg) is dat onbekend; dan laat de guard de
 * pagina gewoon laden, zodat die zelf een melding met "Opnieuw proberen" toont (TO §12.2).
 */
async function gebruiker(sessie: SessionService): Promise<Gebruiker | null | 'onbekend'> {
  try {
    return await sessie.laad();
  } catch {
    return 'onbekend';
  }
}

/** Klantschermen: de beheerder gaat naar zijn eigen startpagina (alleen UX; bank-api beslist). */
export const klantGuard: CanActivateFn = async () => {
  const sessie = inject(SessionService);
  const router = inject(Router);
  const wie = await gebruiker(sessie);
  if (wie === null) {
    return false;
  }
  return sessie.isAdmin() ? router.parseUrl('/admin') : true;
};

/** Admin-schermen alleen voor de beheerder. */
export const adminGuard: CanActivateFn = async () => {
  const sessie = inject(SessionService);
  const router = inject(Router);
  const wie = await gebruiker(sessie);
  if (wie === null) {
    return false;
  }
  return wie === 'onbekend' || sessie.isAdmin() ? true : router.parseUrl('/');
};

/** Rekeningschermen: klant (eigen rekeningen) en beheerder (alleen-lezen). */
export const ingelogdGuard: CanActivateFn = async () => (await gebruiker(inject(SessionService))) !== null;
