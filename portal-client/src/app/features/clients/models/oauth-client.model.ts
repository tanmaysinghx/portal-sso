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
