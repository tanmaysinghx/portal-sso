export type AccessRequestStatus = 'PENDING' | 'APPROVED' | 'DENIED';

export interface AccessRequestUserSummary {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
}

export interface AccessRequestAppSummary {
  id: string;
  name: string;
  category: string;
  iconUrl: string | null;
}

export interface AccessRequest {
  id: string;
  user: AccessRequestUserSummary;
  application: AccessRequestAppSummary;
  reviewer: AccessRequestUserSummary;
  status: AccessRequestStatus;
  reason: string | null;
  reviewerComment: string | null;
  resolvedAt: string | null;
  createdAt: string;
}

export interface AdminSummary {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
}

export interface SubmitAccessRequest {
  applicationId: string;
  reviewerId: string;
  reason?: string;
}
