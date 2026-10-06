import { Page, expect } from '@playwright/test';

/** Het inlogscherm van Keycloak (Nederlands of Engels, afhankelijk van de browser). */
export class LoginPagina {
  constructor(private readonly page: Page) {}

  async open(): Promise<void> {
    await this.page.goto('/');
    await expect(this.page).toHaveURL(/auth\.localtest\.me\/realms\/banksim/);
  }

  async inloggen(gebruiker: string, wachtwoord: string): Promise<void> {
    await this.page.getByLabel(/gebruikersnaam|username/i).fill(gebruiker);
    await this.page.getByLabel(/wachtwoord|password/i).first().fill(wachtwoord);
    await this.page.getByRole('button', { name: /inloggen|sign in|log in/i }).click();
  }

  async foutmelding() {
    return this.page.getByText(/gebruikersnaam of wachtwoord ongeldig|invalid username or password/i);
  }
}
