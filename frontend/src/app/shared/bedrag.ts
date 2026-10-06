import Big from 'big.js';

/**
 * Bedragen (TO §5): de API levert strings met twee decimalen ("-1842.17"); rekenen gebeurt met big.js, nooit met
 * JavaScript-getallen, zodat 0,10 + 0,20 precies 0,30 blijft.
 */

const GELDIG = /^\d{1,13}(\.\d{1,2})?$/;

/**
 * Leest wat een gebruiker typt: "12", "12,5", "12,50", "1.234,56" of "12.50". Geeft null bij iets ongeldigs,
 * meer dan twee decimalen of een negatief bedrag.
 */
export function leesBedrag(invoer: string | null | undefined): Big | null {
  if (invoer === null || invoer === undefined) {
    return null;
  }
  let tekst = invoer.trim().replace(/^€\s*/, '').replace(/\s/g, '');
  if (tekst.includes(',')) {
    tekst = tekst.replace(/\./g, '').replace(',', '.');
  }
  return GELDIG.test(tekst) ? new Big(tekst) : null;
}

/** Naar het contract: altijd twee decimalen met een punt. */
export function naarApi(bedrag: Big): string {
  return bedrag.toFixed(2);
}

/** "1842.17" → "1.842,17"; met {@code teken} een expliciet + of − ervoor. */
export function toonBedrag(bedrag: string | Big, opties: { teken?: boolean; valuta?: boolean } = {}): string {
  const waarde = typeof bedrag === 'string' ? new Big(bedrag) : bedrag;
  const negatief = waarde.lt(0);
  const [heel, decimalen] = waarde.abs().toFixed(2).split('.');
  const metPunten = heel.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  const teken = negatief ? '−' : opties.teken && waarde.gt(0) ? '+' : '';
  const valuta = opties.valuta ? '€ ' : '';
  return `${teken}${valuta}${metPunten},${decimalen}`;
}

export function isAfschrijving(bedrag: string): boolean {
  return new Big(bedrag).lt(0);
}
