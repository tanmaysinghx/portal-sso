import { describe, expect, it } from 'vitest';
import { getIpType, getRelativeTime, parseUserAgent } from './user-agent-parser';

describe('user-agent-parser', () => {
  it('parses Windows Chrome user agent', () => {
    const ua =
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36';
    const parsed = parseUserAgent(ua);
    expect(parsed.browser).toBe('Chrome');
    expect(parsed.os).toBe('Windows');
    expect(parsed.deviceType).toBe('desktop');
    expect(parsed.displayName).toBe('Chrome on Windows 10 / 11');
  });

  it('parses macOS Safari user agent', () => {
    const ua =
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2.1 Safari/605.1.15';
    const parsed = parseUserAgent(ua);
    expect(parsed.browser).toBe('Safari');
    expect(parsed.os).toBe('macOS');
    expect(parsed.deviceType).toBe('desktop');
    expect(parsed.displayName).toBe('Safari on macOS');
  });

  it('parses iPhone Safari user agent', () => {
    const ua =
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3.1 Mobile/15E148 Safari/604.1';
    const parsed = parseUserAgent(ua);
    expect(parsed.browser).toBe('Safari');
    expect(parsed.os).toBe('iOS');
    expect(parsed.deviceType).toBe('mobile');
    expect(parsed.displayName).toBe('Safari on iPhone');
  });

  it('parses iPad Safari user agent', () => {
    const ua =
      'Mozilla/5.0 (iPad; CPU OS 17_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1';
    const parsed = parseUserAgent(ua);
    expect(parsed.browser).toBe('Safari');
    expect(parsed.os).toBe('iPadOS');
    expect(parsed.deviceType).toBe('tablet');
    expect(parsed.displayName).toBe('Safari on iPad');
  });

  it('parses Android Chrome user agent', () => {
    const ua =
      'Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.64 Mobile Safari/537.36';
    const parsed = parseUserAgent(ua);
    expect(parsed.browser).toBe('Chrome');
    expect(parsed.os).toBe('Android');
    expect(parsed.deviceType).toBe('mobile');
    expect(parsed.displayName).toBe('Chrome on Android 14');
  });

  it('parses Edge on Windows user agent', () => {
    const ua =
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 Edg/122.0.2365.92';
    const parsed = parseUserAgent(ua);
    expect(parsed.browser).toBe('Microsoft Edge');
    expect(parsed.deviceType).toBe('desktop');
  });

  it('handles empty or unknown user agent gracefully', () => {
    expect(parseUserAgent('')).toEqual({
      browser: 'Unknown Browser',
      browserVersion: '',
      os: 'Unknown Device',
      osVersion: '',
      deviceType: 'unknown',
      displayName: 'Unknown Device',
    });

    expect(parseUserAgent('Unknown Device')).toEqual({
      browser: 'Unknown Browser',
      browserVersion: '',
      os: 'Unknown Device',
      osVersion: '',
      deviceType: 'unknown',
      displayName: 'Unknown Device',
    });
  });

  describe('getRelativeTime', () => {
    it('returns Active now for current session', () => {
      expect(getRelativeTime(new Date().toISOString(), true)).toBe('Active now');
    });

    it('returns Just now for recent non-current activity', () => {
      const now = new Date();
      expect(getRelativeTime(now.toISOString(), false)).toBe('Just now');
    });

    it('returns minutes ago for activity within an hour', () => {
      const date = new Date(Date.now() - 10 * 60 * 1000);
      expect(getRelativeTime(date.toISOString(), false)).toBe('10m ago');
    });

    it('returns hours ago for activity within a day', () => {
      const date = new Date(Date.now() - 3 * 3600 * 1000);
      expect(getRelativeTime(date.toISOString(), false)).toBe('3h ago');
    });
  });

  describe('getIpType', () => {
    it('identifies localhost addresses', () => {
      expect(getIpType('127.0.0.1')).toBe('Localhost');
      expect(getIpType('::1')).toBe('Localhost');
      expect(getIpType('0:0:0:0:0:0:0:1')).toBe('Localhost');
    });

    it('identifies private LAN addresses', () => {
      expect(getIpType('192.168.1.50')).toBe('Private LAN');
      expect(getIpType('10.0.0.1')).toBe('Private LAN');
      expect(getIpType('172.16.0.1')).toBe('Private LAN');
    });

    it('returns null for public or missing IPs', () => {
      expect(getIpType('203.0.113.195')).toBeNull();
      expect(getIpType(null)).toBeNull();
    });
  });
});
