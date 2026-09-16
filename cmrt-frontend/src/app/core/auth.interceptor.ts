import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './services/auth.service';
import { ToastService } from './services/toast.service';

/**
 * Attaches the bearer token to every API call and turns the backend's error
 * envelope into a toast, so individual components don't each re-implement it.
 * A 401 ends the session; a 403 is surfaced but leaves the user where they are.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const toast = inject(ToastService);

  const token = auth.token;
  const request = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      const message = error.error?.message ?? error.message ?? 'Erreur inattendue';

      if (error.status === 401) {
        // Never bounce the login screen itself - it reports its own failure.
        if (!req.url.includes('/auth/login')) {
          toast.error('Session expirée', 'Veuillez vous reconnecter.');
          auth.logout();
        }
      } else if (error.status === 403) {
        toast.error('Action non autorisée', message);
      } else if (error.status === 0) {
        toast.error('Serveur injoignable', 'Vérifiez que le backend est démarré sur le port 8080.');
      } else if (error.status >= 500) {
        toast.error('Erreur serveur', message);
      }

      return throwError(() => error);
    })
  );
};
