import { execFileSync } from 'node:child_process';
import path from 'node:path';

/** De installatie waartegen getest wordt. */
export const CONTEXT = process.env.BANKSIM_CONTEXT ?? 'kind-single-node';
export const NAMESPACE = 'banksim';
export const SIMULATIEDATUM = '2026-10-05';
export const REPO = path.resolve(import.meta.dirname, '..', '..');

export const KLANT = { gebruiker: 'jdevries', naam: 'Jan de Vries' };
export const ANDERE_KLANT = { gebruiker: 'sbakker', naam: 'Sanne Bakker', iban: 'NL34SIMB0000100021' };
/** Voor de test met een fout wachtwoord: niet de hoofdgebruiker, zodat brute-force-detectie hem niet blokkeert. */
export const KLANT_FOUT_WACHTWOORD = { gebruiker: 'ljansen' };
export const BEHEERDER = { gebruiker: 'beheerder', naam: 'BankSim Beheerder' };

export const AUTH = {
  klant: path.join(import.meta.dirname, '..', '.auth', 'klant.json'),
  beheerder: path.join(import.meta.dirname, '..', '.auth', 'beheerder.json'),
};

export function kubectl(...args: string[]): string {
  return execFileSync('kubectl', ['--context', CONTEXT, '-n', NAMESPACE, ...args], { encoding: 'utf8' });
}

export function geheim(sleutel: string): string {
  return Buffer.from(kubectl('get', 'secret', 'banksim-keycloak', '-o', `jsonpath={.data.${sleutel}}`), 'base64').toString('utf8');
}

export function klantWachtwoord(): string {
  return process.env.BANKSIM_KLANT_PASSWORD ?? geheim('klant-password');
}

export function beheerderWachtwoord(): string {
  return process.env.BANKSIM_BEHEERDER_PASSWORD ?? geheim('beheerder-password');
}

/** "€ 1.899,10" of "−63,48" naar centen, om saldi te vergelijken. */
export function centen(tekst: string): number {
  const schoon = tekst.replace(/[€\s+]/g, '').replace('−', '-').replace(/\./g, '').replace(',', '.');
  return Math.round(parseFloat(schoon) * 100);
}
