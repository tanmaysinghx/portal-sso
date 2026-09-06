import { KeyValuePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { BrandingService } from '../../core/services/branding.service';
import {
  DatabaseMigrationService,
  DatabaseStatusResponse,
  MigrateDatabaseResponse,
  TestConnectionResponse,
} from '../../core/services/database-migration.service';
import { MfaClientService } from '../../core/services/mfa.service';
import { SnackbarService } from '../../core/services/snackbar.service';
import { generateQrCodeSvg } from '../../core/utils/qr-code';

export type SettingsTab = 'branding' | 'security' | 'database' | 'endpoints' | 'system';

interface OidcEndpoint {
  name: string;
  method: string;
  path: string;
  description: string;
}

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [FormsModule, KeyValuePipe],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class Settings {
  readonly brandingService = inject(BrandingService);
  private readonly mfaClientService = inject(MfaClientService);
  private readonly snackbarService = inject(SnackbarService);
  private readonly route = inject(ActivatedRoute);
  readonly dbMigrationService = inject(DatabaseMigrationService);

  readonly activeTab = signal<SettingsTab>('branding');

  // Database Migration State
  readonly dbStatus = signal<DatabaseStatusResponse | null>(null);
  readonly loadingDbStatus = signal(false);
  readonly targetDbType = signal<'POSTGRESQL' | 'MYSQL'>('POSTGRESQL');
  readonly targetHost = signal('localhost');
  readonly targetPort = signal(5432);
  readonly targetDbName = signal('portalsso');
  readonly targetUsername = signal('portal');
  readonly targetPassword = signal('');
  readonly customJdbcUrl = signal('');
  readonly saveConfiguration = signal(true);

  readonly testingConnection = signal(false);
  readonly testResult = signal<TestConnectionResponse | null>(null);

  readonly showMigrateConfirmModal = signal(false);
  readonly migrating = signal(false);
  readonly migrationResult = signal<MigrateDatabaseResponse | null>(null);
  readonly migrationError = signal<string | null>(null);
  readonly restarting = signal(false);
  readonly restartCountdown = signal(6);

  // Branding Form State
  readonly companyNameInput = signal('');
  readonly companyLogoUrlInput = signal('');
  readonly uploadError = signal<string | null>(null);
  readonly copiedPath = signal<string | null>(null);

  // MFA State
  readonly mfaEnabled = signal(false);
  readonly loadingMfa = signal(false);
  readonly showMfaModal = signal(false);
  readonly mfaStep = signal<'setup' | 'recovery'>('setup');
  readonly mfaSecret = signal('');
  readonly mfaProvisioningUri = signal('');
  readonly mfaQrUrl = signal('');
  readonly mfaConfirmCode = signal('');
  readonly mfaConfirmError = signal<string | null>(null);
  readonly mfaSubmitting = signal(false);
  readonly mfaRecoveryCodes = signal<string[]>([]);
  readonly recoveryCodesCopied = signal(false);

  // Disable MFA State
  readonly showDisableModal = signal(false);
  readonly disablePassword = signal('');
  readonly disableError = signal<string | null>(null);
  readonly disableSubmitting = signal(false);

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
    this.loadMfaStatus();

    const tabParam = this.route.snapshot.queryParamMap.get('tab');
    if (tabParam === 'database' || tabParam === 'branding' || tabParam === 'security' || tabParam === 'endpoints' || tabParam === 'system') {
      this.activeTab.set(tabParam);
      if (tabParam === 'database') {
        this.loadDbStatus();
      }
    }
  }

  loadDbStatus(): void {
    this.loadingDbStatus.set(true);
    this.dbMigrationService.getStatus().subscribe({
      next: (res) => {
        this.dbStatus.set(res);
        this.loadingDbStatus.set(false);
      },
      error: () => {
        this.loadingDbStatus.set(false);
      },
    });
  }

  setTargetDbType(type: 'POSTGRESQL' | 'MYSQL'): void {
    this.targetDbType.set(type);
    if (type === 'POSTGRESQL') {
      this.targetPort.set(5432);
    } else {
      this.targetPort.set(3306);
    }
    this.testResult.set(null);
  }

  runTestConnection(): void {
    this.testingConnection.set(true);
    this.testResult.set(null);
    this.dbMigrationService
      .testConnection({
        databaseType: this.targetDbType(),
        host: this.targetHost().trim(),
        port: Number(this.targetPort()),
        databaseName: this.targetDbName().trim(),
        username: this.targetUsername().trim(),
        password: this.targetPassword(),
        customJdbcUrl: this.customJdbcUrl().trim() || undefined,
      })
      .subscribe({
        next: (res) => {
          this.testingConnection.set(false);
          this.testResult.set(res);
          if (res.success) {
            this.snackbarService.success('Connection Successful', `Connected to ${res.databaseProduct ?? this.targetDbType()} database.`);
          } else {
            this.snackbarService.error('Connection Failed', res.message);
          }
        },
        error: (err) => {
          this.testingConnection.set(false);
          const errorMsg = err.error?.message || 'Failed to test database connection.';
          this.testResult.set({ success: false, message: errorMsg });
          this.snackbarService.error('Connection Error', errorMsg);
        },
      });
  }

  openMigrateModal(): void {
    this.migrationError.set(null);
    this.showMigrateConfirmModal.set(true);
  }

  closeMigrateModal(): void {
    if (!this.migrating()) {
      this.showMigrateConfirmModal.set(false);
    }
  }

  executeMigration(): void {
    this.migrating.set(true);
    this.migrationError.set(null);
    this.migrationResult.set(null);

    this.dbMigrationService
      .migrate({
        databaseType: this.targetDbType(),
        host: this.targetHost().trim(),
        port: Number(this.targetPort()),
        databaseName: this.targetDbName().trim(),
        username: this.targetUsername().trim(),
        password: this.targetPassword(),
        customJdbcUrl: this.customJdbcUrl().trim() || undefined,
        saveConfiguration: this.saveConfiguration(),
      })
      .subscribe({
        next: (res) => {
          this.migrating.set(false);
          this.migrationResult.set(res);
          this.showMigrateConfirmModal.set(false);
          this.loadDbStatus();
          this.snackbarService.success('Migration Completed', `Successfully migrated ${res.totalRowsMigrated} records to ${this.targetDbType()}!`);
        },
        error: (err) => {
          this.migrating.set(false);
          const msg = err.error?.message || 'Migration encountered an error. Check server logs.';
          this.migrationError.set(msg);
          this.snackbarService.error('Migration Failed', msg);
        },
      });
  }

  restartApplication(): void {
    if (this.restarting()) return;
    this.restarting.set(true);
    this.restartCountdown.set(6);

    this.dbMigrationService.restart().subscribe({
      next: () => {
        this.runRestartCountdown();
      },
      error: () => {
        // Even if HTTP connection drops due to server shutdown, start the reload countdown
        this.runRestartCountdown();
      },
    });
  }

  private runRestartCountdown(): void {
    const interval = setInterval(() => {
      this.restartCountdown.update((c) => c - 1);
      if (this.restartCountdown() <= 0) {
        clearInterval(interval);
        window.location.reload();
      }
    }, 1000);
  }

  loadMfaStatus(): void {
    this.loadingMfa.set(true);
    this.mfaClientService.getStatus().subscribe({
      next: (res) => {
        this.mfaEnabled.set(res.mfaEnabled);
        this.loadingMfa.set(false);
      },
      error: () => {
        this.loadingMfa.set(false);
      },
    });
  }

  startMfaEnrollment(): void {
    this.mfaSubmitting.set(true);
    this.mfaConfirmError.set(null);
    this.mfaConfirmCode.set('');
    this.mfaStep.set('setup');

    this.mfaClientService.setup().subscribe({
      next: (setup) => {
        this.mfaSecret.set(setup.secret);
        this.mfaProvisioningUri.set(setup.provisioningUri);
        this.mfaQrUrl.set(generateQrCodeSvg(setup.provisioningUri));
        this.mfaSubmitting.set(false);
        this.showMfaModal.set(true);
      },
      error: (err) => {
        this.mfaSubmitting.set(false);
        const msg = err.error?.message || 'Failed to initiate MFA setup.';
        this.snackbarService.error('Setup Error', msg, err.error?.code);
      },
    });
  }

  submitMfaConfirmation(): void {
    const code = this.mfaConfirmCode().trim();
    if (!code || code.length !== 6) {
      this.mfaConfirmError.set('Please enter a valid 6-digit verification code.');
      return;
    }

    this.mfaSubmitting.set(true);
    this.mfaConfirmError.set(null);

    this.mfaClientService.confirm(code).subscribe({
      next: (res) => {
        this.mfaSubmitting.set(false);
        this.mfaEnabled.set(true);
        this.mfaRecoveryCodes.set(res.recoveryCodes);
        this.mfaStep.set('recovery');
        this.snackbarService.success(
          '2FA Enabled Successfully',
          'Your account is now protected with Two-Factor Authentication.'
        );
      },
      error: (err) => {
        this.mfaSubmitting.set(false);
        const msg = err.error?.message || 'Invalid verification code. Please check your app and try again.';
        this.mfaConfirmError.set(msg);
      },
    });
  }

  copyRecoveryCodes(): void {
    const text = this.mfaRecoveryCodes().join('\n');
    navigator.clipboard.writeText(text).then(() => {
      this.recoveryCodesCopied.set(true);
      this.snackbarService.success('Copied', 'Emergency recovery codes copied to clipboard.');
      setTimeout(() => this.recoveryCodesCopied.set(false), 3000);
    });
  }

  closeMfaModal(): void {
    this.showMfaModal.set(false);
    this.loadMfaStatus();
  }

  openDisableModal(): void {
    this.disablePassword.set('');
    this.disableError.set(null);
    this.showDisableModal.set(true);
  }

  closeDisableModal(): void {
    this.showDisableModal.set(false);
    this.disableError.set(null);
  }

  submitDisableMfa(): void {
    const password = this.disablePassword().trim();
    if (!password) {
      this.disableError.set('Password is required to disable Two-Factor Authentication.');
      return;
    }

    this.disableSubmitting.set(true);
    this.disableError.set(null);

    this.mfaClientService.disable(password).subscribe({
      next: () => {
        this.disableSubmitting.set(false);
        this.mfaEnabled.set(false);
        this.showDisableModal.set(false);
        this.snackbarService.info(
          '2FA Disabled',
          'Two-factor authentication has been disabled for your account.'
        );
      },
      error: (err) => {
        this.disableSubmitting.set(false);
        const msg = err.error?.message || 'Incorrect password. Could not disable 2FA.';
        this.disableError.set(msg);
      },
    });
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
