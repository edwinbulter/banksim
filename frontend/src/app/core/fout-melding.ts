import { Component, input, output } from '@angular/core';
import { Foutmelding } from './fout';

/** Foutmelding met een knop "Opnieuw proberen" als dat zin heeft (TO §12.2). */
@Component({
  selector: 'app-fout-melding',
  template: `
    @if (fout(); as melding) {
      <div class="melding melding--fout" role="alert">
        <span>{{ melding.tekst }}</span>
        @if (melding.tijdelijk) {
          <button type="button" class="knop knop--secundair" (click)="opnieuw.emit()">Opnieuw proberen</button>
        }
      </div>
    }
  `,
})
export class FoutMelding {
  readonly fout = input<Foutmelding | null>(null);
  readonly opnieuw = output<void>();
}
