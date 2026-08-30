export type AuditTargetType = 'USER' | 'OAUTH_CLIENT';

export interface AuditEvent {
  id: string;
  actorEmail: string;
  action: string;
  /** Display wording supplied by the server, so the enum is described in exactly one place. */
  actionLabel: string;
  targetType: AuditTargetType;
  targetId: string | null;
  targetLabel: string | null;
  details: string | null;
  ipAddress: string | null;
  userAgent: string | null;
  occurredAt: string;
}

export interface AuditEventPage {
  content: AuditEvent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuditActionOption {
  value: string;
  label: string;
  targetType: AuditTargetType;
}

export interface AuditFilters {
  action: string;
  actor: string;
}
