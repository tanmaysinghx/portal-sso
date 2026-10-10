export interface UserSession {
  id: string;
  creationTime: string;
  lastAccessedTime: string;
  ipAddress: string;
  userAgent: string;
  currentSession: boolean;
}
