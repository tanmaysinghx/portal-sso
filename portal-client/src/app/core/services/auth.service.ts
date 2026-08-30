import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, of, switchMap, tap } from 'rxjs';
import { ADMIN_ROLE, CurrentUser } from '../models/current-user.model';

/**
 * Session state for the admin dashboard. Auth itself is portal-server's own cookie session
 * (form login at /login, same origin via the dev proxy) — this service just tracks who that
 * session belongs to and exposes it as a signal for guards/components to read.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly currentUserSignal = signal<CurrentUser | null>(null);
  private readonly resolvedSignal = signal(false);

  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly resolved = this.resolvedSignal.asReadonly();
  readonly isAdmin = computed(() => this.currentUserSignal()?.roles.includes(ADMIN_ROLE) ?? false);

  /** Re-checks the session (e.g. on app bootstrap / full page reload). Never errors. */
  loadCurrentUser(): Observable<CurrentUser | null> {
    return this.http.get<CurrentUser>('/api/admin/me').pipe(
      tap((user) => this.setUser(user)),
      catchError(() => {
        this.setUser(null);
        return of(null);
      }),
    );
  }

  /**
   * POST /login is form-login's endpoint, shared with the OAuth2 authorization_code flow's
   * server-rendered login page — its success/failure responses are redirect chains not meant
   * for a fetch client, so we deliberately ignore them and confirm the outcome via /me instead.
   */
  login(email: string, password: string, rememberMe = false): Observable<CurrentUser | null> {
    let body = new HttpParams().set('username', email).set('password', password);
    if (rememberMe) {
      body = body.set('remember-me', 'true');
    }
    return this.http
      .post('/login', body.toString(), {
        headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }),
      })
      .pipe(
        catchError(() => of(null)),
        switchMap(() => this.loadCurrentUser()),
      );
  }

  logout(): Observable<void> {
    return this.http.post('/logout', null).pipe(
      catchError(() => of(null)),
      tap(() => this.setUser(null)),
      switchMap(() => of(void 0)),
    );
  }

  private setUser(user: CurrentUser | null): void {
    this.currentUserSignal.set(user);
    this.resolvedSignal.set(true);
  }
}
