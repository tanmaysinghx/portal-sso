import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface PasswordPolicy {
  minLength: number;
  maxLength: number;
  requireUppercase: boolean;
  requireLowercase: boolean;
  requireDigit: boolean;
  requireSymbol: boolean;
}

export interface Account {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  roles: string[];
  createdAt: string | null;
  lastLoginAt: string | null;
  mfaEnabled: boolean;
  /** False when the server has no at-rest encryption key, so enrolment would be refused. */
  mfaAvailable: boolean;
  recoveryCodesRemaining: number;
  passwordPolicy: PasswordPolicy;
}

export interface KnownDevice {
  id: string;
  label: string;
  lastIpAddress: string | null;
  lastLocation: string | null;
  firstSeenAt: string | null;
  lastSeenAt: string;
  /** Matches the browser making the request — "this device". */
  current: boolean;
}

/** Self-service account API (`/api/user/account`). Two-factor enrolment itself is {@link MfaClientService}. */
@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/user/account';

  get(): Observable<Account> {
    return this.http.get<Account>(this.baseUrl);
  }

  updateProfile(firstName: string, lastName: string): Observable<Account> {
    return this.http.patch<Account>(`${this.baseUrl}/profile`, { firstName, lastName });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<{ otherSessionsSignedOut: number }> {
    return this.http.post<{ otherSessionsSignedOut: number }>(`${this.baseUrl}/password`, {
      currentPassword,
      newPassword,
    });
  }

  regenerateRecoveryCodes(code: string): Observable<{ recoveryCodes: string[] }> {
    return this.http.post<{ recoveryCodes: string[] }>(`${this.baseUrl}/recovery-codes`, { code });
  }

  getDevices(): Observable<KnownDevice[]> {
    return this.http.get<KnownDevice[]>(`${this.baseUrl}/devices`);
  }

  forgetDevice(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/devices/${id}`);
  }
}

/**
 * The policy as a checklist, evaluated against what has been typed so far. Mirrors
 * `PasswordPolicy.failures` on the server, which stays the authority — this only saves a round trip.
 */
export function passwordChecklist(policy: PasswordPolicy, value: string): { label: string; met: boolean }[] {
  const rules = [{ label: `At least ${policy.minLength} characters`, met: value.length >= policy.minLength }];
  if (policy.requireUppercase) rules.push({ label: 'An upper-case letter', met: /[A-Z]/.test(value) });
  if (policy.requireLowercase) rules.push({ label: 'A lower-case letter', met: /[a-z]/.test(value) });
  if (policy.requireDigit) rules.push({ label: 'A number', met: /\d/.test(value) });
  if (policy.requireSymbol) rules.push({ label: 'A symbol', met: /[^\p{L}\p{N}\s]/u.test(value) });
  return rules;
}
