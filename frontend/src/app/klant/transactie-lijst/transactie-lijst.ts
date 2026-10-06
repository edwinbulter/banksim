import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { RekeningSoort, TransactiesApi, Transactie } from '../../api';
import { Foutmelding, foutmelding } from '../../core/fout';
import { FoutMelding } from '../../core/fout-melding';
import { BedragPipe } from '../../shared/bedrag.pipe';
import { toonDatum, toonDatumregel, toonDatumTijd } from '../../shared/datum';
import { groepeerPerDag } from '../../shared/groepeer';
import { toonIban } from '../../shared/iban';
import { Zoekcriteria } from '../zoek-formulier/zoek-formulier';

/**
 * Af- en bijschrijvingen (FO): datumregels, per regel naam en bedrag, uitklappen voor details, en "Toon meer"
 * voor de volgende 50. Bij de spaarrekening ook jaartalregels en de spaardetails.
 */
@Component({
  selector: 'app-transactie-lijst',
  imports: [BedragPipe, FoutMelding],
  templateUrl: './transactie-lijst.html',
})
export class TransactieLijst {
  private readonly api = inject(TransactiesApi);

  readonly iban = input.required<string>();
  readonly soort = input.required<RekeningSoort>();
  readonly criteria = input<Zoekcriteria | null>(null);

  protected readonly transacties = signal<Transactie[]>([]);
  protected readonly volgende = signal<string | undefined>(undefined);
  protected readonly laden = signal(false);
  protected readonly geladen = signal(false);
  protected readonly fout = signal<Foutmelding | null>(null);
  protected readonly open = signal<ReadonlySet<string>>(new Set());
  protected readonly dagen = computed(() => groepeerPerDag(this.transacties(), this.soort() === RekeningSoort.SPAAR));

  protected readonly Soort = RekeningSoort;
  protected readonly datumregel = toonDatumregel;
  protected readonly datum = toonDatum;
  protected readonly datumTijd = toonDatumTijd;
  protected readonly ibanTekst = toonIban;

  constructor() {
    // Nieuwe rekening of nieuwe zoekopdracht: opnieuw vanaf het begin.
    effect(() => {
      this.iban();
      this.criteria();
      untracked(() => void this.laadEerste());
    });
  }

  protected async laadEerste(): Promise<void> {
    this.transacties.set([]);
    this.volgende.set(undefined);
    this.open.set(new Set());
    await this.laad(undefined);
  }

  protected async toonMeer(): Promise<void> {
    await this.laad(this.volgende());
  }

  protected wissel(id: string): void {
    const open = new Set(this.open());
    if (open.has(id)) {
      open.delete(id);
    } else {
      open.add(id);
    }
    this.open.set(open);
  }

  protected opnieuw(): void {
    void (this.transacties().length === 0 ? this.laadEerste() : this.toonMeer());
  }

  private async laad(cursor: string | undefined): Promise<void> {
    const criteria = this.criteria();
    this.laden.set(true);
    this.fout.set(null);
    try {
      const pagina = await firstValueFrom(this.api.getTransacties({
        iban: this.iban(),
        cursor,
        size: 50,
        q: criteria?.tekst || undefined,
        min: criteria?.min,
        max: criteria?.max,
        type: criteria?.type,
        direction: criteria?.richting,
      }));
      this.transacties.update((huidige) => [...huidige, ...pagina.items]);
      this.volgende.set(pagina.nextCursor || undefined);
      this.geladen.set(true);
    } catch (fout) {
      this.fout.set(foutmelding(fout));
    } finally {
      this.laden.set(false);
    }
  }
}
