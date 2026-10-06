import { Injectable, signal } from '@angular/core';

/** De simulatiedatum zoals de API hem laatst meldde; getoond in de kopbalk (FO). */
@Injectable({ providedIn: 'root' })
export class SimulatiedatumService {
  readonly datum = signal<string | null>(null);
}
