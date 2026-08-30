import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';

export interface PasswordPolicy {
  minLength: number;
  maxLength: number;
  requireUppercase: boolean;
  requireLowercase: boolean;
  requireDigit: boolean;
  requireSymbol: boolean;
}

export interface RegistrationPolicy {
  enabled: boolean;
  requiresApproval: boolean;
  /** Absent only if the server is older than the policy endpoint change. */
  passwordPolicy?: PasswordPolicy;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
}

export interface RegistrationResult {
  email: string;
  pendingApproval: boolean;
}

/**
 * Self-registration is off by default and controlled server-side, so the UI has to ask before it
 * can offer a sign-up link — hard-coding one would show a dead end on most deployments.
 */
@Injectable({ providedIn: 'root' })
export class RegistrationService {
  private readonly http = inject(HttpClient);

  private readonly policySignal = signal<RegistrationPolicy>({ enabled: false, requiresApproval: false });
  readonly policy = this.policySignal.asReadonly();

  /** Never errors: if the policy can't be read, registration stays hidden. */
  loadPolicy(): Observable<RegistrationPolicy> {
    return this.http.get<RegistrationPolicy>('/api/public/registration-policy').pipe(
      tap((policy) => this.policySignal.set(policy)),
      catchError(() => of(this.policySignal())),
    );
  }

  register(request: RegisterRequest): Observable<RegistrationResult> {
    return this.http.post<RegistrationResult>('/api/public/register', request);
  }
}
