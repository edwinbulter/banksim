import { Component, OnInit, inject, input, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import Big from 'big.js';
import { Subject, debounceTime, distinctUntilChanged, firstValueFrom, switchMap, of, catchError } from 'rxjs';
import { BetalingenApi, Contact, ContactenApi, RekeningDetail, RekeningenApi } from '../../api';
import { Foutmelding, foutmelding } from '../../core/fout';
import { FoutMelding } from '../../core/fout-melding';
import { leesBedrag, naarApi, toonBedrag } from '../../shared/bedrag';
import { BedragPipe } from '../../shared/bedrag.pipe';
import { toonIban } from '../../shared/iban';

/** Geldig bedrag: groter dan 0 en hooguit 2 decimalen. */
function bedragValidator(control: AbstractControl<string>): ValidationErrors | null {
  const bedrag = leesBedrag(control.value);
  return bedrag && bedrag.gt(0) ? null : { bedrag: true };
}

/**
 * Betalen-scherm (FO): alleen naar bekende contacten. Na "Volgende" volgt een controlestap; pas "Bevestigen"
 * boekt. Elke bevestiging krijgt één Idempotency-Key, die bij opnieuw proberen hetzelfde blijft, zodat een
 * betaling nooit dubbel wordt geboekt (TO §6).
 */
@Component({
  selector: 'app-betalen',
  imports: [ReactiveFormsModule, RouterLink, BedragPipe, FoutMelding],
  templateUrl: './betalen.html',
})
export class Betalen implements OnInit {
  private readonly rekeningen = inject(RekeningenApi);
  private readonly contacten = inject(ContactenApi);
  private readonly betalingen = inject(BetalingenApi);
  private readonly router = inject(Router);

  readonly iban = input.required<string>();

  protected readonly van = signal<RekeningDetail | null>(null);
  protected readonly stap = signal<'invoer' | 'controle'>('invoer');
  protected readonly suggesties = signal<Contact[]>([]);
  protected readonly fout = signal<Foutmelding | null>(null);
  protected readonly bezig = signal(false);
  protected readonly ibanTekst = toonIban;
  private idempotencyKey: string | null = null;
  private readonly zoekterm = new Subject<string>();

  protected readonly formulier = new FormGroup({
    bedrag: new FormControl('', { nonNullable: true, validators: [Validators.required, bedragValidator] }),
    naamOntvanger: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(70)] }),
    ibanOntvanger: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(34)] }),
    omschrijving: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(140)] }),
    betalingskenmerk: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(25)] }),
    extraOmschrijving: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(35)] }),
  });

  constructor() {
    this.zoekterm.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap((q) => q.trim().length < 2 ? of([]) : this.contacten.getContacten({ q: q.trim() }).pipe(catchError(() => of([])))),
    ).subscribe((lijst) => this.suggesties.set(lijst));
  }

  ngOnInit(): void {
    void this.laad();
  }

  protected async laad(): Promise<void> {
    this.fout.set(null);
    try {
      this.van.set(await firstValueFrom(this.rekeningen.getRekening({ iban: this.iban() })));
    } catch (fout) {
      this.fout.set(foutmelding(fout));
    }
  }

  protected zoekContact(tekst: string): void {
    this.zoekterm.next(tekst);
  }

  protected kies(contact: Contact): void {
    this.formulier.patchValue({ naamOntvanger: contact.naam, ibanOntvanger: toonIban(contact.iban) });
    this.suggesties.set([]);
  }

  /** Waarschuwing als het bedrag hoger is dan het saldo; de bank beslist uiteindelijk. */
  protected boveSaldo(): boolean {
    const bedrag = leesBedrag(this.formulier.controls.bedrag.value);
    const saldo = this.van()?.saldo;
    return !!bedrag && !!saldo && bedrag.gt(new Big(saldo));
  }

  protected volgende(): void {
    this.formulier.markAllAsTouched();
    if (this.formulier.invalid) {
      return;
    }
    this.fout.set(null);
    this.idempotencyKey = crypto.randomUUID();
    this.stap.set('controle');
  }

  protected wijzigen(): void {
    this.idempotencyKey = null;
    this.stap.set('invoer');
  }

  protected bedragTekst(): string {
    const bedrag = leesBedrag(this.formulier.controls.bedrag.value);
    return bedrag ? toonBedrag(bedrag, { valuta: true }) : '';
  }

  protected async bevestigen(): Promise<void> {
    const waarde = this.formulier.getRawValue();
    const bedrag = leesBedrag(waarde.bedrag);
    if (!bedrag || !this.idempotencyKey) {
      return;
    }
    this.bezig.set(true);
    this.fout.set(null);
    try {
      await firstValueFrom(this.betalingen.betaal({
        idempotencyKey: this.idempotencyKey,
        betaalOpdracht: {
          vanIban: this.iban(),
          bedrag: naarApi(bedrag),
          naamOntvanger: waarde.naamOntvanger.trim(),
          ibanOntvanger: waarde.ibanOntvanger.replace(/\s/g, '').toUpperCase(),
          omschrijving: waarde.omschrijving.trim() || undefined,
          betalingskenmerk: waarde.betalingskenmerk.trim() || undefined,
          extraOmschrijving: waarde.extraOmschrijving.trim() || undefined,
        },
      }));
      await this.router.navigate(['/betaalrekening', this.iban()], {
        state: { bevestiging: `Betaling van ${toonBedrag(bedrag, { valuta: true })} aan ${waarde.naamOntvanger.trim()} is uitgevoerd.` },
      });
    } catch (fout) {
      const melding = foutmelding(fout);
      this.fout.set(melding);
      if (!melding.tijdelijk) {
        // Afgewezen: bij een nieuwe poging hoort een nieuwe sleutel.
        this.idempotencyKey = crypto.randomUUID();
      }
    } finally {
      this.bezig.set(false);
    }
  }
}
