export interface PortalUser {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  enabled: boolean;
  accountLocked: boolean;
  mfaEnabled: boolean;
  roles: string[];
  lastLoginAt: string | null;
  createdAt: string;
}

export interface CreateUserRequest {
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
  roles?: string[];
  enabled?: boolean;
}
