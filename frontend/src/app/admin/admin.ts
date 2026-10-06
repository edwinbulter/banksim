import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AdminApi, RekeninghouderSamenvatting, Simulatiedatum } from '../api';
import { Foutmelding, foutmelding } from '../core/fout';
import { FoutMelding } from '../core/fout-melding';
import { SimulatiedatumService } from '../core/simulatiedatum.service';
import { BedragPipe } from '../shared/bedrag.pipe';
import { toonDatum } from '../shared/datum';
import { toonIban } from '../shared/iban';

/** Admin-scherm (FO): simulatiedatum instellen en de rekeninghouders bekijken (alleen lezen). */
@Component({
  selector: 'app-admin',
  imports: [FormsModule, RouterLink, BedragPipe, FoutMelding],
  templateUrl: './admin.html',
})
export class Admin implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly simulatiedatumService = inject(SimulatiedatumService);

  protected readonly simulatiedatum = signal<Simulatiedatum | null>(null);
  protected readonly rekeninghouders = signal<RekeninghouderSamenvatting[]>([]);
  protected readonly zoek = signal('');
  protected readonly gefilterd = computed(() => {
    const zoek = this.zoek().trim().toLowerCase();
    return this.rekeninghouders().filter((h) => h.naam.toLowerCase().includes(zoek));
  });
  protected readonly fout = signal<Foutmelding | null>(null);
  protected readonly bevestiging = signal<string | null>(null);
  protected nieuweDatum = '';
  protected readonly datum = toonDatum;
  protected readonly ibanTekst = toonIban;

  ngOnInit(): void {
    void this.laad();
  }

  protected async laad(): Promise<void> {
    this.fout.set(null);
    try {
      const [datum, houders] = await Promise.all([
        firstValueFrom(this.api.getSimulatiedatum()),
        firstValueFrom(this.api.getRekeninghouders({})),
      ]);
      this.toon(datum);
      this.rekeninghouders.set(houders);
    } catch (fout) {
      this.fout.set(foutmelding(fout));
    }
  }

  protected async toepassen(): Promise<void> {
    await this.zet(this.nieuweDatum || undefined);
  }

  protected async vandaag(): Promise<void> {
    await this.zet(undefined);
  }

  private async zet(datum: string | undefined): Promise<void> {
    this.fout.set(null);
    this.bevestiging.set(null);
    try {
      const resultaat = await firstValueFrom(this.api.zetSimulatiedatum({ simulatiedatumWijziging: { datum } }));
      this.toon(resultaat);
      this.rekeninghouders.set(await firstValueFrom(this.api.getRekeninghouders({})));
      this.bevestiging.set(`De simulatiedatum is nu ${toonDatum(resultaat.datum)}.`);
    } catch (fout) {
      this.fout.set(foutmelding(fout));
    }
  }

  private toon(datum: Simulatiedatum): void {
    this.simulatiedatum.set(datum);
    this.nieuweDatum = datum.datum;
    this.simulatiedatumService.datum.set(datum.datum);
  }
}
