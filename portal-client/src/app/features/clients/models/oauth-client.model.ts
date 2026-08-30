export interface OAuthClient {
  id: string;
  clientId: string;
  clientName: string;
  redirectUris: string[];
  scopes: string[];
  enabled: boolean;
  createdAt: string;
}

export interface CreateOAuthClientRequest {
  clientId: string;
  clientName: string;
  redirectUris: string[];
  scopes: string[];
}

/** No `clientId`: it is immutable server-side, since relying apps are configured with it. */
export interface UpdateOAuthClientRequest {
  clientName: string;
  redirectUris: string[];
  scopes: string[];
  enabled: boolean;
}
