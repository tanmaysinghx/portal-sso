export type StatsRange = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR' | 'FIVE_YEARS' | 'ALL';

export interface Totals {
  totalUsers: number;
  newUsers: number;
  enabledUsers: number;
  lockedUsers: number;
  totalClients: number;
  logins: number;
  failedLogins: number;
  uniqueIps: number;
  countries: number;
}

export interface TimePoint {
  bucket: string;
  count: number;
}

export interface LoginPoint {
  bucket: string;
  successful: number;
  failed: number;
}

export interface LabelCount {
  label: string;
  count: number;
}

export interface ClientStat {
  clientId: string | null;
  clientName: string;
  logins: number;
  uniqueUsers: number;
}

export interface CountryStat {
  /** ISO 3166-1 alpha-2, or null for addresses with no country (loopback, private range). */
  code: string | null;
  name: string;
  logins: number;
}

export interface RecentLogin {
  email: string;
  successful: boolean;
  ipAddress: string | null;
  countryCode: string | null;
  countryName: string | null;
  clientId: string | null;
  occurredAt: string;
}

export interface DashboardStats {
  range: StatsRange;
  bucket: 'HOUR' | 'DAY' | 'MONTH';
  from: string;
  to: string;
  totals: Totals;
  signups: TimePoint[];
  logins: LoginPoint[];
  lastLoginBuckets: LabelCount[];
  byClient: ClientStat[];
  byCountry: CountryStat[];
  recentLogins: RecentLogin[];
  /** False when no GeoIP database is configured, so the map can explain itself instead of sitting empty. */
  geoDatabaseAvailable: boolean;
}

export const RANGE_OPTIONS: { value: StatsRange; label: string }[] = [
  { value: 'DAY', label: '24h' },
  { value: 'WEEK', label: '7d' },
  { value: 'MONTH', label: '30d' },
  { value: 'YEAR', label: '1y' },
  { value: 'FIVE_YEARS', label: '5y' },
  { value: 'ALL', label: 'All' },
];
