import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../models';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

/** Blocks routes for signed-out visitors and remembers where they were heading. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isLoggedIn()) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { redirect: state.url } });
};

/** Keeps signed-in users away from the login and signup screens. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isLoggedIn() ? router.createUrlTree(['/dashboard']) : true;
};

/**
 * Restricts a route to the roles listed on its {@code data.roles}. Mirrors the
 * server-side rule so the UI never offers an action the API would refuse.
 */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);

  const allowed = (route.data?.['roles'] as Role[] | undefined) ?? [];
  if (allowed.length === 0 || auth.hasRole(...allowed)) {
    return true;
  }
  toast.error('Accès refusé', 'Votre rôle ne donne pas accès à cette section.');
  return router.createUrlTree(['/dashboard']);
};
