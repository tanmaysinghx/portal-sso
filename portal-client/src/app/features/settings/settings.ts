import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BrandingService } from '../../core/services/branding.service';
import { SnackbarService } from '../../core/services/snackbar.service';

export type SettingsTab = 'branding' | 'security' | 'endpoints' | 'system';

interface OidcEndpoint {
  name: string;
  method: string;
  path: string;
  description: string;
}

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class Settings {
  readonly brandingService = inject(BrandingService);
  private readonly snackbarService = inject(SnackbarService);

  readonly activeTab = signal<SettingsTab>('branding');

  // Branding Form State
  readonly companyNameInput = signal('');
  readonly companyLogoUrlInput = signal('');
  readonly uploadError = signal<string | null>(null);
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

  constructor() {
    this.companyNameInput.set(this.brandingService.companyName() ?? '');
    this.companyLogoUrlInput.set(this.brandingService.companyLogoUrl() ?? '');
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) {
      return;
    }

    const file = input.files[0];
    if (!file.type.startsWith('image/')) {
      this.uploadError.set('Please select a valid image file (PNG, SVG, JPG, WebP).');
      return;
    }

    if (file.size > 2 * 1024 * 1024) {
      this.uploadError.set('Image file size must be less than 2MB.');
      return;
    }

    this.uploadError.set(null);
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        this.companyLogoUrlInput.set(reader.result);
      }
    };
    reader.readAsDataURL(file);
  }

  saveBranding(): void {
    const name = this.companyNameInput().trim() || null;
    const logo = this.companyLogoUrlInput().trim() || null;

    this.brandingService.saveBranding(name, logo);

    if (logo) {
      this.snackbarService.success(
        'Branding Updated',
        `Custom logo and organization name "${name || 'Portal SSO'}" saved successfully.`
      );
    } else {
      this.snackbarService.info('Branding Updated', 'Organization branding updated.');
    }
  }

  resetBranding(): void {
    this.companyNameInput.set('');
    this.companyLogoUrlInput.set('');
    this.brandingService.resetToDefault();
    this.snackbarService.info('Branding Reset', 'Restored default Portal SSO logo and title.');
  }

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
