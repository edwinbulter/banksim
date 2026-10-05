import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, InjectionToken, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface Gebruiker {
  naam: string;
  gebruikersnaam: string;
  rollen: string[];
}

export const LOGIN_URL = '/oauth2/authorization/keycloak';

/** Volledige paginanavigatie (naar de login of logout van de BFF); te vervangen in tests. */
export const BROWSER_NAVIGATIE = new InjectionToken<(url: string) => void>('BROWSER_NAVIGATIE', {
  providedIn: 'root',
  factory: () => (url: string) => window.location.assign(url),
});

/** De BFF houdt de sessie bij; de app vraagt alleen wie er is ingelogd (TO §2). */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly http = inject(HttpClient);
  private readonly navigeer = inject(BROWSER_NAVIGATIE);

  readonly gebruiker = signal<Gebruiker | null>(null);

  async laad(): Promise<void> {
    try {
      this.gebruiker.set(await firstValueFrom(this.http.get<Gebruiker>('/api/me')));
    } catch (fout) {
      if (fout instanceof HttpErrorResponse && fout.status === 401) {
        this.navigeer(LOGIN_URL);
        return;
      }
      throw fout;
    }
  }
}
