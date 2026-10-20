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
  /** Days a password lasts before it must be changed; 0 means passwords never expire. */
  maxAgeDays: number;
  /** How many days ahead of expiry to start warning. */
  expiryWarningDays: number;
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
  passwordChangedAt: string | null;
  /** Null when expiry is switched off. */
  passwordExpiresAt: string | null;
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

export type PasswordExpiryState = 'never' | 'ok' | 'warning' | 'expired';

export interface PasswordExpiry {
  state: PasswordExpiryState;
  /** Whole days until expiry, rounded up so "expires in 1 day" means "before this time tomorrow". */
  daysLeft: number | null;
  /** Days since the password was set, rounded down. */
  ageDays: number | null;
  /** 0–1 share of the allowed lifetime already used; null when expiry is off. */
  usedFraction: number | null;
  /** Short human phrasing: "Expires in 12 days", "Expires today", "Expired". */
  label: string;
}

const DAY_MS = 24 * 60 * 60 * 1000;

/** Everything the console says about when a password expires, from the account and the clock. */
export function passwordExpiry(account: Account, now: Date = new Date()): PasswordExpiry {
  const changed = account.passwordChangedAt ? new Date(account.passwordChangedAt).getTime() : null;
  const ageDays = changed === null ? null : Math.max(0, Math.floor((now.getTime() - changed) / DAY_MS));

  if (!account.passwordExpiresAt) {
    return { state: 'never', daysLeft: null, ageDays, usedFraction: null, label: 'Never expires' };
  }

  const msLeft = new Date(account.passwordExpiresAt).getTime() - now.getTime();
  const maxAgeMs = account.passwordPolicy.maxAgeDays * DAY_MS;
  const usedFraction = maxAgeMs > 0 ? Math.min(1, Math.max(0, 1 - msLeft / maxAgeMs)) : null;

  if (msLeft <= 0) {
    return { state: 'expired', daysLeft: 0, ageDays, usedFraction: 1, label: 'Expired' };
  }
  const daysLeft = Math.ceil(msLeft / DAY_MS);
  const state: PasswordExpiryState = daysLeft <= account.passwordPolicy.expiryWarningDays ? 'warning' : 'ok';
  const label = msLeft < DAY_MS ? 'Expires today' : `Expires in ${daysLeft} day${daysLeft === 1 ? '' : 's'}`;
  return { state, daysLeft, ageDays, usedFraction, label };
}
