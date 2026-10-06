/** Datumweergave volgens het FO, altijd in Nederlandse tijd. */

const ZONE = 'Europe/Amsterdam';

const datumregel = new Intl.DateTimeFormat('nl-NL', {
  weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC',
});
const datum = new Intl.DateTimeFormat('nl-NL', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });
const datumMetZone = new Intl.DateTimeFormat('nl-NL', { day: 'numeric', month: 'long', year: 'numeric', timeZone: ZONE });
const tijd = new Intl.DateTimeFormat('nl-NL', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: ZONE });

/** "2026-10-02" als middernacht UTC, zodat de dag nooit verschuift door tijdzones. */
function alsDag(isoDatum: string): Date {
  const [jaar, maand, dag] = isoDatum.split('-').map((deel) => parseInt(deel, 10));
  return new Date(Date.UTC(jaar, maand - 1, dag));
}

/** "2026-10-02" → "Vrijdag 2 oktober 2026" (datumregel in de transactielijst). */
export function toonDatumregel(isoDatum: string): string {
  const tekst = datumregel.format(alsDag(isoDatum));
  return tekst.charAt(0).toUpperCase() + tekst.slice(1);
}

/** "2026-10-03" → "3 oktober 2026" (Uitgevoerd op). */
export function toonDatum(isoDatum: string): string {
  return datum.format(alsDag(isoDatum));
}

/** "2026-10-03T11:26:00Z" → "3 oktober 2026 om 13:26" (Datum transactie). */
export function toonDatumTijd(isoTijdstip: string): string {
  const moment = new Date(isoTijdstip);
  return `${datumMetZone.format(moment)} om ${tijd.format(moment)}`;
}
