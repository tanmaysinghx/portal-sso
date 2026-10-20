import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { PasswordPolicy } from './account.service';

export interface ResetInspection {
  /** The account the link belongs to. Safe to show — whoever has the link already knows it. */
  email: string;
  passwordPolicy: PasswordPolicy;
}

/** "Forgot password?" (`/api/public/password-reset`). Tokens travel in request bodies, never a query string. */
@Injectable({ providedIn: 'root' })
export class PasswordResetService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/public/password-reset';

  /** False when the server cannot send mail, so sign-in pages can hide the link. */
  readonly available = signal(false);

  loadPolicy(): Observable<{ available: boolean }> {
    return this.http
      .get<{ available: boolean }>(`${this.baseUrl}/policy`)
      .pipe(tap((policy) => this.available.set(policy.available)));
  }

  /** Same response for every address; show the message it returns rather than composing one. */
  request(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.baseUrl}/request`, { email });
  }

  inspect(token: string): Observable<ResetInspection> {
    return this.http.post<ResetInspection>(`${this.baseUrl}/inspect`, { token });
  }

  confirm(token: string, newPassword: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.baseUrl}/confirm`, { token, newPassword });
  }
}
