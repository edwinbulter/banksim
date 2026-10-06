import { Location } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { RekeningDetail, RekeningSoort, RekeningenApi } from '../../api';
import { Foutmelding, foutmelding } from '../../core/fout';
import { FoutMelding } from '../../core/fout-melding';
import { SessionService } from '../../core/session.service';
import { SimulatiedatumService } from '../../core/simulatiedatum.service';
import { BedragPipe } from '../../shared/bedrag.pipe';
import { toonIban } from '../../shared/iban';
import { TransactieLijst } from '../transactie-lijst/transactie-lijst';
import { ZoekFormulier, Zoekcriteria } from '../zoek-formulier/zoek-formulier';

/** Betaalrekening-scherm (FO): kop, Betalen, Zoeken in transacties en de transactielijst. */
@Component({
  selector: 'app-betaalrekening',
  imports: [RouterLink, BedragPipe, FoutMelding, TransactieLijst, ZoekFormulier],
  templateUrl: './betaalrekening.html',
})
export class Betaalrekening implements OnInit {
  private readonly api = inject(RekeningenApi);
  private readonly simulatiedatum = inject(SimulatiedatumService);
  protected readonly sessie = inject(SessionService);
  private readonly router = inject(Router);
  private readonly locatie = inject(Location);

  readonly iban = input.required<string>();

  protected readonly rekening = signal<RekeningDetail | null>(null);
  protected readonly fout = signal<Foutmelding | null>(null);
  protected readonly zoekenOpen = signal(false);
  protected readonly criteria = signal<Zoekcriteria | null>(null);
  protected readonly bevestiging = signal<string | null>(null);
  protected readonly Soort = RekeningSoort;
  protected readonly ibanTekst = toonIban;

  constructor() {
    const melding = this.router.currentNavigation()?.extras.state?.['bevestiging'];
    if (typeof melding === 'string') {
      this.bevestiging.set(melding);
    }
  }

  ngOnInit(): void {
    void this.laad();
  }

  protected async laad(): Promise<void> {
    this.fout.set(null);
    try {
      const rekening = await firstValueFrom(this.api.getRekening({ iban: this.iban() }));
      this.rekening.set(rekening);
      this.simulatiedatum.datum.set(rekening.simulatiedatum);
    } catch (fout) {
      this.fout.set(foutmelding(fout));
    }
  }

  protected terug(): void {
    this.locatie.back();
  }

  protected wisselZoeken(): void {
    this.zoekenOpen.update((open) => !open);
  }

  protected zoek(criteria: Zoekcriteria): void {
    this.criteria.set(criteria);
  }

  protected wis(): void {
    this.criteria.set(null);
  }
}
