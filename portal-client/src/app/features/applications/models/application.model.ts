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
  externalAuthEnabled: boolean;
  externalAuthApiKeyIssued: boolean;
  externalAuthApiKeyPrefix: string | null;
  createdAt: string;
  updatedAt: string;
  externalRegistrationEnabled: boolean;
}

export interface UserApplication {
  id: string;
  name: string;
  description: string | null;
  appUrl: string;
  iconUrl: string | null;
  category: string;
  clientId: string | null;
  accessible: boolean;
  pendingRequestStatus: string | null;
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
  externalAuthEnabled?: boolean;
  externalRegistrationEnabled?: boolean;
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
  externalAuthEnabled?: boolean;
  externalRegistrationEnabled?: boolean;
}

/** Optional new OAuth client created alongside the app. Every field has a server-side default. */
export interface NewOAuthClientRequest {
  clientId?: string;
  redirectUris?: string[];
  scopes?: string[];
  confidential?: boolean;
  requireConsent?: boolean;
}

export interface OnboardApplicationRequest {
  application: CreateApplicationRequest;
  oauthClient?: NewOAuthClientRequest;
}

/**
 * Everything an app team needs, in one place. `clientSecret` and `apiKey` are only present in
 * the response that created them; later reads return null and `apiKeyPrefix` identifies the key.
 */
export interface ApplicationCredentials {
  issuerUrl: string;
  discoveryUrl: string;
  oidc: {
    clientId: string;
    clientSecret: string | null;
    confidential: boolean;
    redirectUris: string[];
    scopes: string[];
    popupCallbackUri: string;
    clientExists: boolean;
  } | null;
  externalAuth: {
    headerName: string;
    apiKey: string | null;
    apiKeyPrefix: string | null;
    apiKeyIssued: boolean;
    registrationEnabled: boolean;
    loginEndpoint: string;
    registerEndpoint: string | null;
  } | null;
}

export interface ApplicationOnboardingResponse {
  application: Application;
  credentials: ApplicationCredentials;
}

export interface ApplicationApiKeyResponse {
  apiKey: string;
  apiKeyPrefix: string;
  headerName: string;
  registerEndpoint: string;
  loginEndpoint: string;
}
