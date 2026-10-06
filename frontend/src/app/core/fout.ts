import { HttpErrorResponse } from '@angular/common/http';

/** Een fout zoals de gebruiker hem te zien krijgt; ProblemDetail-detail van de API als die er is (TO §11). */
export interface Foutmelding {
  tekst: string;
  /** Opnieuw proberen heeft zin (netwerk, 503, 429). */
  tijdelijk: boolean;
  /** Code uit het ProblemDetail-type, bijvoorbeeld "saldo-ontoereikend". */
  code?: string;
}

export function foutmelding(fout: unknown): Foutmelding {
  if (fout instanceof HttpErrorResponse) {
    if (fout.status === 0) {
      return { tekst: 'Er is geen verbinding met de bank. Controleer je internetverbinding en probeer het opnieuw.', tijdelijk: true };
    }
    const probleem = fout.error as { detail?: string; type?: string } | null;
    const code = probleem?.type?.split('/').pop();
    const tijdelijk = fout.status === 503 || fout.status === 502 || fout.status === 504 || fout.status === 429;
    if (probleem?.detail) {
      return { tekst: probleem.detail, tijdelijk, code };
    }
    if (tijdelijk) {
      return { tekst: 'De bank is even niet bereikbaar. Probeer het over een paar seconden opnieuw.', tijdelijk, code };
    }
  }
  return { tekst: 'Er ging iets mis. Probeer het later opnieuw.', tijdelijk: false };
}
