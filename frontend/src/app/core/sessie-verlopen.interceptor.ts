import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { SessionService } from './session.service';

/** Een 401 van de BFF betekent: sessie verlopen. Dan opnieuw inloggen via Keycloak. */
export const sessieVerlopenInterceptor: HttpInterceptorFn = (request, next) => {
  const sessie = inject(SessionService);
  return next(request).pipe(
    catchError((fout: unknown) => {
      if (fout instanceof HttpErrorResponse && fout.status === 401 && request.url !== '/api/me') {
        sessie.naarLogin();
      }
      return throwError(() => fout);
    }),
  );
};
