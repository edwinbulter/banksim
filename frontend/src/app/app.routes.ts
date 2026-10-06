import { Routes } from '@angular/router';
import { adminGuard, ingelogdGuard, klantGuard } from './core/guards';

/** Schermen uit het FO; de rollen zijn alleen UX, bank-api controleert elke aanroep (TO §10.3). */
export const routes: Routes = [
  { path: '', canActivate: [klantGuard], loadComponent: () => import('./klant/overzicht/overzicht').then((m) => m.Overzicht) },
  { path: 'betaalrekening/:iban', canActivate: [ingelogdGuard], loadComponent: () => import('./klant/betaalrekening/betaalrekening').then((m) => m.Betaalrekening) },
  { path: 'spaarrekening/:iban', canActivate: [ingelogdGuard], loadComponent: () => import('./klant/spaarrekening/spaarrekening').then((m) => m.Spaarrekening) },
  { path: 'betalen/:iban', canActivate: [klantGuard], loadComponent: () => import('./klant/betalen/betalen').then((m) => m.Betalen) },
  { path: 'overschrijven', canActivate: [klantGuard], loadComponent: () => import('./klant/overschrijven/overschrijven').then((m) => m.Overschrijven) },
  { path: 'admin', canActivate: [adminGuard], loadComponent: () => import('./admin/admin').then((m) => m.Admin) },
  { path: 'admin/rekeninghouders/:id', canActivate: [adminGuard], loadComponent: () => import('./klant/overzicht/overzicht').then((m) => m.Overzicht) },
  { path: '**', redirectTo: '' },
];
