import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideApi } from './api';
import { routes } from './app.routes';
import { sessieVerlopenInterceptor } from './core/sessie-verlopen.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Routeparameters (:iban, ?van=…) komen als inputs binnen.
    provideRouter(routes, withComponentInputBinding()),
    // XSRF: Angular stuurt het XSRF-TOKEN-cookie standaard mee als X-XSRF-TOKEN-header (TO §10.1).
    provideHttpClient(withInterceptors([sessieVerlopenInterceptor])),
    provideApi(''),
  ],
};
