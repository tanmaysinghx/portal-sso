import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, of } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Guards routes requiring an authenticated user (any role).
 * Unauthenticated visitors are redirected to /sign-in.
 */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const decide = () => {
    if (!authService.currentUser()) {
      return router.parseUrl('/sign-in');
    }
    return true;
  };

  if (authService.resolved()) {
    return of(decide());
  }
  return authService.loadCurrentUser().pipe(map(decide));
};
