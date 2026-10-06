import { Component, output } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TransactieType } from '../../api';
import { leesBedrag, naarApi } from '../../shared/bedrag';

/** Wat de lijst met de API-parameters doet; bedragen al naar het contractformaat. */
export interface Zoekcriteria {
  tekst?: string;
  min?: string;
  max?: string;
  type?: TransactieType;
  richting: 'ALL' | 'OUT' | 'IN';
}

/** De zeven typen van de betaalrekening (FO), met de labels uit het contract. */
export const BETAAL_TYPEN: { waarde: TransactieType; label: string }[] = [
  { waarde: TransactieType.ONLINE_BANKIEREN, label: 'Online bankieren' },
  { waarde: TransactieType.IDEAL_WERO, label: 'iDEAL | Wero' },
  { waarde: TransactieType.BETAALAUTOMAAT, label: 'Betaalautomaat' },
  { waarde: TransactieType.INCASSO, label: 'Incasso' },
  { waarde: TransactieType.GELDAUTOMAAT, label: 'Geldautomaat' },
  { waarde: TransactieType.OVERSCHRIJVING, label: 'Overschrijving' },
  { waarde: TransactieType.VERZAMELBETALING, label: 'Verzamelbetaling' },
];

/** Zoekformulier (FO §Zoeken in transacties). */
@Component({
  selector: 'app-zoek-formulier',
  imports: [ReactiveFormsModule],
  templateUrl: './zoek-formulier.html',
})
export class ZoekFormulier {
  readonly zoek = output<Zoekcriteria>();
  readonly wis = output<void>();

  protected readonly typen = BETAAL_TYPEN;
  protected melding: string | null = null;

  protected readonly formulier = new FormGroup({
    tekst: new FormControl('', { nonNullable: true }),
    min: new FormControl('', { nonNullable: true }),
    max: new FormControl('', { nonNullable: true }),
    type: new FormControl<TransactieType | ''>('', { nonNullable: true }),
    richting: new FormControl<'ALL' | 'OUT' | 'IN'>('ALL', { nonNullable: true }),
  });

  protected zoeken(): void {
    const waarde = this.formulier.getRawValue();
    const min = waarde.min.trim() ? leesBedrag(waarde.min) : undefined;
    const max = waarde.max.trim() ? leesBedrag(waarde.max) : undefined;
    if (min === null || max === null) {
      this.melding = 'Vul een geldig bedrag in, bijvoorbeeld 12,50.';
      return;
    }
    if (min && max && min.gt(max)) {
      this.melding = '"Bedrag van" mag niet groter zijn dan "Bedrag t/m".';
      return;
    }
    this.melding = null;
    this.zoek.emit({
      tekst: waarde.tekst.trim() || undefined,
      min: min ? naarApi(min) : undefined,
      max: max ? naarApi(max) : undefined,
      type: waarde.type || undefined,
      richting: waarde.richting,
    });
  }

  protected wissen(): void {
    this.formulier.reset();
    this.melding = null;
    this.wis.emit();
  }
}
