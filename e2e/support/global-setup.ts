import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { CONTEXT, REPO, SIMULATIEDATUM, geheim } from './omgeving';

/**
 * Vóór de suite: testdata terug naar de beginstand (vaste seed) met een vaste simulatiedatum, en de wachtwoorden
 * uit het Kubernetes Secret voor de tests klaarzetten.
 */
export default async function globalSetup(): Promise<void> {
  process.env.BANKSIM_KLANT_PASSWORD ??= geheim('klant-password');
  process.env.BANKSIM_BEHEERDER_PASSWORD ??= geheim('beheerder-password');
  if (process.env.BANKSIM_E2E_GEEN_RESET === 'true') {
    return;
  }
  execFileSync(path.join(REPO, 'deploy', 'scripts', 'reset-data.sh'),
    ['--context', CONTEXT, '--simulatiedatum', SIMULATIEDATUM], { stdio: 'inherit' });
  // bank-api cachet de simulatiedatum 5 seconden.
  await new Promise((klaar) => setTimeout(klaar, 6_000));
}
