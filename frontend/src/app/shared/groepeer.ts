import { Transactie } from '../api';

export interface Dag {
  datum: string;
  /** Gezet als dit de eerste dag van een (vorig) jaar in de lijst is: dan komt er een jaartalregel boven. */
  nieuwJaar?: number;
  transacties: Transactie[];
}

/**
 * Groepeert een lijst (nieuwste eerst) per datum. Met {@code jaartallen} krijgt de eerste dag van elk volgend
 * (ouder) jaar een jaartalregel, zoals het FO voor de spaarrekening vraagt.
 */
export function groepeerPerDag(transacties: Transactie[], jaartallen = false): Dag[] {
  const dagen: Dag[] = [];
  for (const transactie of transacties) {
    const laatste = dagen.at(-1);
    if (laatste && laatste.datum === transactie.datum) {
      laatste.transacties.push(transactie);
      continue;
    }
    const jaar = parseInt(transactie.datum.substring(0, 4), 10);
    const vorigJaar = laatste ? parseInt(laatste.datum.substring(0, 4), 10) : undefined;
    dagen.push({
      datum: transactie.datum,
      nieuwJaar: jaartallen && vorigJaar !== undefined && jaar !== vorigJaar ? jaar : undefined,
      transacties: [transactie],
    });
  }
  return dagen;
}
