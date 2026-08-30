export interface OAuthClient {
  id: string;
  clientId: string;
  clientName: string;
  redirectUris: string[];
  scopes: string[];
  enabled: boolean;
  logoUrl?: string | null;
  requireConsent?: boolean;
  /** True when the client authenticates with a secret rather than PKCE alone. */
  confidential: boolean;
  createdAt: string;
}

/**
 * The create response, and the only time a client secret is ever visible. `clientSecret` is null
 * for public (PKCE) clients.
 */
export interface OAuthClientCreated {
  client: OAuthClient;
  clientSecret: string | null;
}

export interface CreateOAuthClientRequest {
  clientId: string;
  clientName: string;
  redirectUris: string[];
  scopes: string[];
  logoUrl?: string;
  requireConsent?: boolean;
  /** Issues a secret for server-side apps. Leave false for browser and mobile clients. */
  confidential?: boolean;
}

/** No `clientId`: it is immutable server-side, since relying apps are configured with it. */
export interface UpdateOAuthClientRequest {
  clientName: string;
  redirectUris: string[];
  scopes: string[];
  enabled: boolean;
}
