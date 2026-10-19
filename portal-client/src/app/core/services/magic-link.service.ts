import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

export interface MagicLinkPolicy {
  enabled: boolean;
  registrationEnabled: boolean;
}

export interface MagicLinkRequestResponse {
  message: string;
}

@Injectable({ providedIn: 'root' })
export class MagicLinkService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/public/magic-link';

  /**
   * Cached so the sign-in page can decide whether to show the passwordless option without
   * flickering it in after the password form has already rendered.
   */
  readonly policy = signal<MagicLinkPolicy>({ enabled: false, registrationEnabled: false });

  loadPolicy(): Observable<MagicLinkPolicy> {
    return this.http
      .get<MagicLinkPolicy>(`${this.baseUrl}/policy`)
      .pipe(tap((policy) => this.policy.set(policy)));
  }

  /**
   * The response is deliberately the same whether or not an account exists — the server does not
   * reveal that, so neither can this. Show the message it returns rather than composing one here.
   */
  requestLink(email: string): Observable<MagicLinkRequestResponse> {
    return this.http.post<MagicLinkRequestResponse>(`${this.baseUrl}/request`, { email });
  }
}
