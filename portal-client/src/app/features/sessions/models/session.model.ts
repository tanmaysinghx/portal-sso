import { ParsedSessionDevice } from '../utils/user-agent-parser';

export interface UserSession {
  id: string;
  creationTime: string;
  lastAccessedTime: string;
  ipAddress: string;
  userAgent: string;
  currentSession?: boolean;
  isCurrent?: boolean;
}

export interface EnrichedUserSession extends UserSession {
  isCurrent: boolean;
  device: ParsedSessionDevice;
  relativeLastActive: string;
  ipType: string | null;
  expandedDetails?: boolean;
}
