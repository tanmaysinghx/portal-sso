import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, of } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Guards every dashboard route: not logged in -> /sign-in, logged in but not ROLE_ADMIN ->
 * /forbidden. This parent-level guard re-runs on every child navigation within the dashboard
 * (Angular re-evaluates guards up the whole activated-route chain, not just once per subtree
 * entry) — only hit the network the first time; every navigation after that reads the
 * already-resolved signal.
 */
export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const decide = () => {
    if (!authService.currentUser()) {
      return router.parseUrl('/sign-in');
    }
    if (!authService.isAdmin()) {
      return router.parseUrl('/forbidden');
    }
    return true;
  };

  if (authService.resolved()) {
    return of(decide());
  }
  return authService.loadCurrentUser().pipe(map(decide));
};
