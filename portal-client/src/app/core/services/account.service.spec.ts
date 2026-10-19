import { describe, expect, it } from 'vitest';
import { PasswordPolicy, passwordChecklist } from './account.service';

const POLICY: PasswordPolicy = {
  minLength: 10,
  maxLength: 100,
  requireUppercase: true,
  requireLowercase: true,
  requireDigit: true,
  requireSymbol: false,
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
