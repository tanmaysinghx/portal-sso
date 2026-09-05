export type ApplicationAccessType = 'ALL_USERS' | 'RESTRICTED';

export interface RoleSummary {
  id: string;
  name: string;
  description?: string | null;
}

export interface Application {
  id: string;
  name: string;
  description: string | null;
  appUrl: string;
  iconUrl: string | null;
  category: string;
  clientId: string | null;
  accessType: ApplicationAccessType;
  roles: RoleSummary[];
  enabled: boolean;
  displayOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface UserApplication {
  id: string;
  name: string;
  description: string | null;
  appUrl: string;
  iconUrl: string | null;
  category: string;
  clientId: string | null;
}

export interface CreateApplicationRequest {
  name: string;
  description?: string;
  appUrl: string;
  iconUrl?: string;
  category?: string;
  clientId?: string;
  accessType: ApplicationAccessType;
  roleIds?: string[];
  enabled?: boolean;
  displayOrder?: number;
}

export interface UpdateApplicationRequest {
  name: string;
  description?: string;
  appUrl: string;
  iconUrl?: string;
  category?: string;
  clientId?: string;
  accessType: ApplicationAccessType;
  roleIds?: string[];
  enabled?: boolean;
  displayOrder?: number;
}
