export interface PortalRole {
  id: string;
  name: string;
  description: string | null;
  userCount: number;
  /** Platform roles the application depends on; the server refuses to delete them. */
  protectedRole: boolean;
  createdAt: string;
}

export interface CreateRoleRequest {
  name: string;
  description?: string;
}
