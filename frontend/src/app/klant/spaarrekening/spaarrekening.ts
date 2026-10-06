import { Location } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { RekeningDetail, RekeningSoort, RekeningenApi } from '../../api';
import { Foutmelding, foutmelding } from '../../core/fout';
import { FoutMelding } from '../../core/fout-melding';
import { SessionService } from '../../core/session.service';
import { SimulatiedatumService } from '../../core/simulatiedatum.service';
import { BedragPipe } from '../../shared/bedrag.pipe';
import { toonIban } from '../../shared/iban';
import { TransactieLijst } from '../transactie-lijst/transactie-lijst';

/** Spaarrekening-scherm (FO): saldo, lopende rente, Opnemen/Inleggen en de af- en bijschrijvingen. */
@Component({
  selector: 'app-spaarrekening',
  imports: [RouterLink, BedragPipe, FoutMelding, TransactieLijst],
  templateUrl: './spaarrekening.html',
})
export class Spaarrekening implements OnInit {
  private readonly api = inject(RekeningenApi);
  private readonly simulatiedatum = inject(SimulatiedatumService);
  private readonly locatie = inject(Location);
  protected readonly sessie = inject(SessionService);

  readonly iban = input.required<string>();

  protected readonly rekening = signal<RekeningDetail | null>(null);
  protected readonly fout = signal<Foutmelding | null>(null);
  protected readonly Soort = RekeningSoort;
  protected readonly ibanTekst = toonIban;

  protected terug(): void {
    this.locatie.back();
  }

  protected percentage(waarde: string | undefined): string {
    return (waarde ?? '').replace('.', ',');
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
}
