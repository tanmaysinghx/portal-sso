import { Component, inject, signal } from '@angular/core';
import { SnackbarService } from '../../../../core/services/snackbar.service';

interface OidcEndpoint {
  name: string;
  method: string;
  path: string;
  description: string;
}

/** Read-only reference for integrators: OIDC endpoints, token lifetimes and signing keys. */
@Component({
  selector: 'app-protocol-settings',
  standalone: true,
  imports: [],
  templateUrl: './protocol-settings.html',
})
export class ProtocolSettings {
  private readonly snackbarService = inject(SnackbarService);

  readonly copiedPath = signal<string | null>(null);

  readonly endpoints: OidcEndpoint[] = [
    {
      name: 'OpenID Discovery Document',
      method: 'GET',
      path: '/.well-known/openid-configuration',
      description: 'Auto-discovery configuration metadata (RFC 8414)',
    },
    {
      name: 'OAuth 2.1 Authorization Endpoint',
      method: 'GET',
      path: '/oauth2/authorize',
      description: 'Initiates PKCE authorization code grant with client app',
    },
    {
      name: 'OAuth 2.1 Token Exchange',
      method: 'POST',
      path: '/oauth2/token',
      description: 'Exchanges authorization code for signed JWT ID/Access tokens',
    },
    {
      name: 'JSON Web Key Set (JWKS)',
      method: 'GET',
      path: '/oauth2/jwks',
      description: 'Public 2048-bit RSA keys for token signature verification',
    },
    {
      name: 'UserInfo Claims Endpoint',
      method: 'GET',
      path: '/userinfo',
      description: 'Standard OIDC user identity claims profile',
    },
    {
      name: 'Token Revocation Endpoint',
      method: 'POST',
      path: '/oauth2/revoke',
      description: 'Revokes active refresh tokens and grant chains (RFC 7009)',
    },
  ];

  copyToClipboard(text: string, path: string): void {
    const fullUrl = window.location.origin + text;
    navigator.clipboard.writeText(fullUrl).then(() => {
      this.copiedPath.set(path);
      this.snackbarService.success('URL Copied', `Copied ${fullUrl} to clipboard.`);
      setTimeout(() => {
        if (this.copiedPath() === path) {
          this.copiedPath.set(null);
        }
      }, 2500);
    });
  }
}
