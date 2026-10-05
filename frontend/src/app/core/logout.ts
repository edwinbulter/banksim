import { Component, ElementRef, viewChild } from '@angular/core';

/**
 * Uitloggen is een gewone form-POST naar de BFF (met CSRF-token), zodat de browser de redirect naar de
 * Keycloak-logout volgt.
 */
@Component({
  selector: 'app-logout',
  template: `
    <form #formulier method="post" action="/logout" (submit)="vulCsrfToken()">
      <input type="hidden" name="_csrf" />
      <button type="submit">Uitloggen</button>
    </form>
  `,
})
export class Logout {
  private readonly formulier = viewChild.required<ElementRef<HTMLFormElement>>('formulier');

  protected vulCsrfToken(): void {
    const token = document.cookie
      .split('; ')
      .find((cookie) => cookie.startsWith('XSRF-TOKEN='))
      ?.substring('XSRF-TOKEN='.length);
    const veld = this.formulier().nativeElement.elements.namedItem('_csrf') as HTMLInputElement;
    veld.value = token ? decodeURIComponent(token) : '';
  }
}
