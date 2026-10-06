import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { BetalingenApi, RekeningDetail, RekeningSoort, RekeningenApi } from '../../api';
import { Foutmelding, foutmelding } from '../../core/fout';
import { FoutMelding } from '../../core/fout-melding';
import { leesBedrag, naarApi } from '../../shared/bedrag';
import { BedragPipe } from '../../shared/bedrag.pipe';
import { toonIban } from '../../shared/iban';

/** Overschrijven tussen de eigen betaal- en spaarrekening (FO: Opnemen en Inleggen). */
@Component({
  selector: 'app-overschrijven',
  imports: [ReactiveFormsModule, RouterLink, BedragPipe, FoutMelding],
  templateUrl: './overschrijven.html',
})
export class Overschrijven implements OnInit {
  private readonly rekeningen = inject(RekeningenApi);
  private readonly betalingen = inject(BetalingenApi);
  private readonly router = inject(Router);

  readonly van = input.required<string>();
  readonly naar = input.required<string>();

  protected readonly vanRekening = signal<RekeningDetail | null>(null);
  protected readonly naarRekening = signal<RekeningDetail | null>(null);
  protected readonly fout = signal<Foutmelding | null>(null);
  protected readonly bezig = signal(false);
  protected readonly ibanTekst = toonIban;
  private idempotencyKey = crypto.randomUUID();

  protected readonly formulier = new FormGroup({
    bedrag: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    omschrijving: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(140)] }),
  });
  protected ongeldigBedrag = false;

  ngOnInit(): void {
    void this.laad(this.van(), this.naar());
  }

  protected async laad(van: string, naar: string): Promise<void> {
    this.fout.set(null);
    try {
      const [a, b] = await Promise.all([
        firstValueFrom(this.rekeningen.getRekening({ iban: van })),
        firstValueFrom(this.rekeningen.getRekening({ iban: naar })),
      ]);
      this.vanRekening.set(a);
      this.naarRekening.set(b);
    } catch (fout) {
      this.fout.set(foutmelding(fout));
    }
  }

  protected wissel(): void {
    const van = this.vanRekening();
    this.vanRekening.set(this.naarRekening());
    this.naarRekening.set(van);
    this.idempotencyKey = crypto.randomUUID();
  }

  protected soortNaam(rekening: RekeningDetail): string {
    return rekening.soort === RekeningSoort.SPAAR ? 'Spaarrekening' : 'Betaalrekening';
  }

  protected spaarIban(): string | undefined {
    return [this.vanRekening(), this.naarRekening()].find((r) => r?.soort === RekeningSoort.SPAAR)?.iban;
  }

  protected async overschrijven(): Promise<void> {
    const van = this.vanRekening();
    const naar = this.naarRekening();
    const bedrag = leesBedrag(this.formulier.controls.bedrag.value);
    this.ongeldigBedrag = !bedrag || !bedrag.gt(0);
    if (!van || !naar || !bedrag || this.ongeldigBedrag || this.formulier.invalid) {
      return;
    }
    this.bezig.set(true);
    this.fout.set(null);
    try {
      await firstValueFrom(this.betalingen.schrijfOver({
        idempotencyKey: this.idempotencyKey,
        overschrijfOpdracht: {
          vanIban: van.iban,
          naarIban: naar.iban,
          bedrag: naarApi(bedrag),
          omschrijving: this.formulier.controls.omschrijving.value.trim() || undefined,
        },
      }));
      await this.router.navigate(['/spaarrekening', this.spaarIban()]);
    } catch (fout) {
      const melding = foutmelding(fout);
      this.fout.set(melding);
      if (!melding.tijdelijk) {
        this.idempotencyKey = crypto.randomUUID();
      }
    } finally {
      this.bezig.set(false);
    }
  }
}
