import { describe, expect, it } from 'vitest';
import { Account, PasswordPolicy, passwordChecklist, passwordExpiry } from './account.service';

const POLICY: PasswordPolicy = {
  minLength: 10,
  maxLength: 100,
  requireUppercase: true,
  requireLowercase: true,
  requireDigit: true,
  requireSymbol: false,
  maxAgeDays: 90,
  expiryWarningDays: 14,
};

describe('passwordChecklist', () => {
  it('lists only the rules the policy enables', () => {
    const labels = passwordChecklist(POLICY, '').map((r) => r.label);
    expect(labels).toEqual(['At least 10 characters', 'An upper-case letter', 'A lower-case letter', 'A number']);
  });

  it('marks each rule independently', () => {
    const result = passwordChecklist(POLICY, 'lowercase1');
    expect(result.map((r) => r.met)).toEqual([true, false, true, true]);
  });

  it('is fully met by a compliant password', () => {
    expect(passwordChecklist(POLICY, 'Compliant123').every((r) => r.met)).toBe(true);
  });

  it('treats any non-alphanumeric, non-space character as a symbol', () => {
    const policy = { ...POLICY, requireSymbol: true };
    const symbolRule = (value: string) => passwordChecklist(policy, value).find((r) => r.label === 'A symbol')!.met;
    expect(symbolRule('Password123')).toBe(false);
    expect(symbolRule('Password 123')).toBe(false);
    expect(symbolRule('Password!123')).toBe(true);
  });
});

describe('passwordExpiry', () => {
  const now = new Date('2026-10-19T12:00:00Z');
  const account = (changedDaysAgo: number, maxAgeDays = 90) =>
    ({
      passwordPolicy: { ...POLICY, maxAgeDays },
      passwordChangedAt: new Date(now.getTime() - changedDaysAgo * 86_400_000).toISOString(),
      passwordExpiresAt:
        maxAgeDays === 0 ? null : new Date(now.getTime() + (maxAgeDays - changedDaysAgo) * 86_400_000).toISOString(),
    }) as Account;

  it('reports never when expiry is off', () => {
    const e = passwordExpiry(account(400, 0), now);
    expect(e.state).toBe('never');
    expect(e.ageDays).toBe(400);
  });

  it('counts down outside the warning window', () => {
    const e = passwordExpiry(account(30), now);
    expect(e).toMatchObject({ state: 'ok', daysLeft: 60, ageDays: 30, label: 'Expires in 60 days' });
    expect(e.usedFraction).toBeCloseTo(30 / 90);
  });

  it('warns inside the window, with singular phrasing at one day', () => {
    expect(passwordExpiry(account(80), now).state).toBe('warning');
    expect(passwordExpiry(account(89), now).label).toBe('Expires in 1 day');
  });

  it('says today in the last hours and expired after', () => {
    const lastHours = { ...account(0), passwordExpiresAt: new Date(now.getTime() + 3_600_000).toISOString() } as Account;
    expect(passwordExpiry(lastHours, now).label).toBe('Expires today');
    expect(passwordExpiry(account(91), now)).toMatchObject({ state: 'expired', daysLeft: 0, usedFraction: 1 });
  });
});
