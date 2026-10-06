import { Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AdminApi, MeApi, RekeningOverzicht, RekeningSoort } from '../../api';
import { Foutmelding, foutmelding } from '../../core/fout';
import { FoutMelding } from '../../core/fout-melding';
import { SimulatiedatumService } from '../../core/simulatiedatum.service';
import { BedragPipe } from '../../shared/bedrag.pipe';
import { toonIban } from '../../shared/iban';

/** Overzichtscherm (FO): de betaal- en spaarrekeningen van de klant. De beheerder ziet het via een rekeninghouder-id. */
@Component({
  selector: 'app-overzicht',
  imports: [RouterLink, BedragPipe, FoutMelding],
  templateUrl: './overzicht.html',
})
export class Overzicht implements OnInit {
  private readonly me = inject(MeApi);
  private readonly admin = inject(AdminApi);
  private readonly simulatiedatum = inject(SimulatiedatumService);

  /** Route-parameter: alleen in de beheerdersweergave. */
  readonly id = input<string>();

  protected readonly overzicht = signal<RekeningOverzicht | null>(null);
  protected readonly fout = signal<Foutmelding | null>(null);
  protected readonly iban = toonIban;

  ngOnInit(): void {
    void this.laad();
  }

  protected async laad(): Promise<void> {
    this.fout.set(null);
    try {
      const id = this.id();
      const overzicht = await firstValueFrom(id
        ? this.admin.getRekeningenVanRekeninghouder({ id })
        : this.me.getMijnRekeningen());
      this.overzicht.set(overzicht);
      this.simulatiedatum.datum.set(overzicht.simulatiedatum);
    } catch (fout) {
      this.fout.set(foutmelding(fout));
    }
  }

  protected betaal() {
    return this.overzicht()?.rekeningen.filter((r) => r.soort === RekeningSoort.BETAAL) ?? [];
  }

  protected spaar() {
    return this.overzicht()?.rekeningen.filter((r) => r.soort === RekeningSoort.SPAAR) ?? [];
  }
}
