import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, InjectionToken, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Gebruiker } from '../api';

export const LOGIN_URL = '/oauth2/authorization/keycloak';

/** Volledige paginanavigatie (naar de login of logout van de BFF); te vervangen in tests. */
export const BROWSER_NAVIGATIE = new InjectionToken<(url: string) => void>('BROWSER_NAVIGATIE', {
  providedIn: 'root',
  factory: () => (url: string) => window.location.assign(url),
});

/**
 * De BFF houdt de sessie bij; de app vraagt alleen wie er is ingelogd (TO §2). De rollen bepalen alleen wat de
 * app laat zien; bank-api controleert elke aanroep zelf.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly http = inject(HttpClient);
  private readonly navigeer = inject(BROWSER_NAVIGATIE);
  private geladen?: Promise<Gebruiker | null>;

  readonly gebruiker = signal<Gebruiker | null>(null);
  readonly isAdmin = computed(() => this.gebruiker()?.rollen.includes('admin') ?? false);

  /** Eén keer per pagina geladen; guards en de kopbalk wachten op hetzelfde antwoord. */
  laad(): Promise<Gebruiker | null> {
    this.geladen ??= this.haalOp();
    return this.geladen;
  }

  naarLogin(): void {
    this.navigeer(LOGIN_URL);
  }

  private async haalOp(): Promise<Gebruiker | null> {
    try {
      const gebruiker = await firstValueFrom(this.http.get<Gebruiker>('/api/me'));
      this.gebruiker.set(gebruiker);
      return gebruiker;
    } catch (fout) {
      this.geladen = undefined;
      if (fout instanceof HttpErrorResponse && fout.status === 401) {
        this.naarLogin();
        return null;
      }
      throw fout;
    }
  }
}
