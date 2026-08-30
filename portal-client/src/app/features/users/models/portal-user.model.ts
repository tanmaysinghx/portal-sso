export interface PortalUser {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  enabled: boolean;
  accountLocked: boolean;
  roles: string[];
  lastLoginAt: string | null;
  createdAt: string;
}
