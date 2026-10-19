import { DatePipe, KeyValuePipe } from '@angular/common';
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
import {
  EmailService,
  EmailSettingsResponse,
  EmailTemplateType,
} from '../../core/services/email.service';
import { MfaClientService } from '../../core/services/mfa.service';
import { SnackbarService } from '../../core/services/snackbar.service';
import {
  SendTestEmailResponse,
  SmtpService,
  SmtpSettingsResponse,
  SmtpTransportSecurity,
  UpdateSmtpSettingsRequest,
} from '../../core/services/smtp.service';
import { generateQrCodeSvg } from '../../core/utils/qr-code';

export type SettingsTab =
  | 'branding'
  | 'security'
  | 'email'
  | 'templates'
  | 'database'
  | 'endpoints'
  | 'system';

interface OidcEndpoint {
  name: string;
  method: string;
  path: string;
  description: string;
}

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [DatePipe, FormsModule, KeyValuePipe],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class Settings {
  readonly brandingService = inject(BrandingService);
  private readonly mfaClientService = inject(MfaClientService);
  private readonly snackbarService = inject(SnackbarService);
  private readonly route = inject(ActivatedRoute);
  readonly dbMigrationService = inject(DatabaseMigrationService);
  private readonly smtpService = inject(SmtpService);
  private readonly emailService = inject(EmailService);

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

  // Email (SMTP) State
  readonly smtpSettings = signal<SmtpSettingsResponse | null>(null);
  readonly loadingSmtp = signal(false);
  readonly savingSmtp = signal(false);
  readonly smtpEnabled = signal(false);
  readonly smtpHost = signal('');
  readonly smtpPort = signal<number | null>(587);
  readonly smtpTransportSecurity = signal<SmtpTransportSecurity>('STARTTLS');
  readonly smtpUsername = signal('');
  // Starts empty even when a password is stored: the server never sends the secret back, so there
  // is nothing to prefill. Left empty on save it means "keep the stored one" — smtpPasswordTouched
  // is what separates that from "the administrator cleared this box on purpose".
  readonly smtpPassword = signal('');
  readonly smtpPasswordTouched = signal(false);
  readonly smtpFromAddress = signal('');
  readonly smtpFromName = signal('');
  readonly smtpReplyTo = signal('');
  readonly smtpError = signal<string | null>(null);

  readonly testRecipient = signal('');
  readonly sendingTestEmail = signal(false);
  readonly testEmailResult = signal<SendTestEmailResponse | null>(null);

  // Email Templates & Notifications State
  readonly emailSettings = signal<EmailSettingsResponse | null>(null);
  readonly loadingEmail = signal(false);
  readonly savingEmail = signal(false);
  readonly emailError = signal<string | null>(null);

  readonly emailProductName = signal('');
  readonly emailLogoUrl = signal('');
  readonly emailAccentColor = signal('#F59E0B');
  readonly emailSupportUrl = signal('');
  readonly emailFooterNote = signal('');

  readonly notifyWelcome = signal(true);
  readonly notifyNewDevice = signal(true);
  readonly notifySignIn = signal(false);
  readonly magicLinkEnabled = signal(false);
  readonly magicLinkRegistrationEnabled = signal(false);
  readonly magicLinkTtlMinutes = signal(15);

  readonly previewRecipient = signal('');
  readonly previewSending = signal<EmailTemplateType | null>(null);

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
    if (
      tabParam === 'database' ||
      tabParam === 'branding' ||
      tabParam === 'security' ||
      tabParam === 'email' ||
      tabParam === 'templates' ||
      tabParam === 'endpoints' ||
      tabParam === 'system'
    ) {
      this.activeTab.set(tabParam);
      if (tabParam === 'database') {
        this.loadDbStatus();
      }
      if (tabParam === 'email') {
        this.loadSmtpSettings();
      }
      if (tabParam === 'templates') {
        this.loadEmailSettings();
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

  loadSmtpSettings(): void {
    this.loadingSmtp.set(true);
    this.smtpError.set(null);
    this.smtpService.getSettings().subscribe({
      next: (res) => {
        this.applySmtpSettings(res);
        this.loadingSmtp.set(false);
      },
      error: (err) => {
        this.loadingSmtp.set(false);
        this.smtpError.set(err.error?.message || 'Failed to load email settings.');
      },
    });
  }

  private applySmtpSettings(res: SmtpSettingsResponse): void {
    this.smtpSettings.set(res);
    this.smtpEnabled.set(res.enabled);
    this.smtpHost.set(res.host ?? '');
    this.smtpPort.set(res.port ?? 587);
    this.smtpTransportSecurity.set(res.transportSecurity);
    this.smtpUsername.set(res.username ?? '');
    this.smtpFromAddress.set(res.fromAddress ?? '');
    this.smtpFromName.set(res.fromName ?? '');
    this.smtpReplyTo.set(res.replyToAddress ?? '');
    this.smtpPassword.set('');
    this.smtpPasswordTouched.set(false);
  }

  /** Default port for the selected transport, so the two cannot drift out of step by accident. */
  setSmtpTransportSecurity(mode: SmtpTransportSecurity): void {
    this.smtpTransportSecurity.set(mode);
    if (mode === 'SSL_TLS' && (this.smtpPort() === 587 || this.smtpPort() === 25)) {
      this.smtpPort.set(465);
    } else if (mode === 'STARTTLS' && (this.smtpPort() === 465 || this.smtpPort() === 25)) {
      this.smtpPort.set(587);
    } else if (mode === 'NONE' && (this.smtpPort() === 465 || this.smtpPort() === 587)) {
      this.smtpPort.set(25);
    }
    this.testEmailResult.set(null);
  }

  onSmtpPasswordInput(value: string): void {
    this.smtpPassword.set(value);
    this.smtpPasswordTouched.set(true);
  }

  private buildSmtpRequest(): UpdateSmtpSettingsRequest {
    return {
      enabled: this.smtpEnabled(),
      host: this.smtpHost().trim() || null,
      port: this.smtpPort() === null ? null : Number(this.smtpPort()),
      transportSecurity: this.smtpTransportSecurity(),
      username: this.smtpUsername().trim() || null,
      // Omitted entirely unless the box was typed in, which is how the server knows to leave the
      // stored secret alone. An empty string sent after touching it clears the password.
      ...(this.smtpPasswordTouched() ? { password: this.smtpPassword() } : {}),
      fromAddress: this.smtpFromAddress().trim() || null,
      fromName: this.smtpFromName().trim() || null,
      replyToAddress: this.smtpReplyTo().trim() || null,
    };
  }

  saveSmtpSettings(): void {
    this.savingSmtp.set(true);
    this.smtpError.set(null);
    this.smtpService.saveSettings(this.buildSmtpRequest()).subscribe({
      next: (res) => {
        this.savingSmtp.set(false);
        this.applySmtpSettings(res);
        this.snackbarService.success(
          'Email Settings Saved',
          res.usable
            ? `Outbound email is on and will be sent via ${res.host}.`
            : 'Settings saved. Outbound email is currently off.',
        );
      },
      error: (err) => {
        this.savingSmtp.set(false);
        const msg = err.error?.message || 'Failed to save email settings.';
        this.smtpError.set(msg);
        this.snackbarService.error('Save Failed', msg, err.error?.code);
      },
    });
  }

  /**
   * Tests the values currently in the form rather than the stored ones, so a relay can be proved
   * before it is committed. The password follows the same rule as save: untouched means the server
   * falls back to the stored secret.
   */
  sendTestEmail(): void {
    const recipient = this.testRecipient().trim();
    if (!recipient) {
      this.testEmailResult.set({
        success: false,
        message: 'Enter an address to send the test message to.',
        detail: null,
      });
      return;
    }

    this.sendingTestEmail.set(true);
    this.testEmailResult.set(null);
    this.smtpService.sendTestEmail({ recipient, settings: this.buildSmtpRequest() }).subscribe({
      next: (res) => {
        this.sendingTestEmail.set(false);
        this.testEmailResult.set(res);
        if (res.success) {
          this.snackbarService.success('Test Email Sent', res.message);
        } else {
          this.snackbarService.error('Test Email Failed', res.detail || res.message);
        }
        // The server records the outcome against the stored row, so refresh to show it.
        this.smtpService.getSettings().subscribe({
          next: (settings) => this.smtpSettings.set(settings),
        });
      },
      error: (err) => {
        this.sendingTestEmail.set(false);
        const msg = err.error?.message || 'Could not send the test email.';
        this.testEmailResult.set({ success: false, message: msg, detail: null });
        this.snackbarService.error('Test Email Failed', msg, err.error?.code);
      },
    });
  }

  loadEmailSettings(): void {
    this.loadingEmail.set(true);
    this.emailError.set(null);
    this.emailService.getSettings().subscribe({
      next: (res) => {
        this.applyEmailSettings(res);
        this.loadingEmail.set(false);
      },
      error: (err) => {
        this.loadingEmail.set(false);
        this.emailError.set(err.error?.message || 'Failed to load email template settings.');
      },
    });
  }

  private applyEmailSettings(res: EmailSettingsResponse): void {
    this.emailSettings.set(res);
    this.emailProductName.set(res.productName ?? '');
    this.emailLogoUrl.set(res.logoUrl ?? '');
    this.emailAccentColor.set(res.accentColor ?? '#F59E0B');
    this.emailSupportUrl.set(res.supportUrl ?? '');
    this.emailFooterNote.set(res.footerNote ?? '');
    this.notifyWelcome.set(res.welcomeEnabled);
    this.notifyNewDevice.set(res.newDeviceEnabled);
    this.notifySignIn.set(res.signInNoticeEnabled);
    this.magicLinkEnabled.set(res.magicLinkEnabled);
    this.magicLinkRegistrationEnabled.set(res.magicLinkRegistrationEnabled);
    this.magicLinkTtlMinutes.set(res.magicLinkTtlMinutes);
  }

  /**
   * Passwordless registration is meaningless without passwordless sign-in — the link that creates
   * an account is the same mechanism. Turning the parent off turns the child off with it rather
   * than leaving a checked box that does nothing.
   */
  setMagicLinkEnabled(enabled: boolean): void {
    this.magicLinkEnabled.set(enabled);
    if (!enabled) {
      this.magicLinkRegistrationEnabled.set(false);
    }
  }

  saveEmailSettings(): void {
    this.savingEmail.set(true);
    this.emailError.set(null);
    this.emailService
      .saveSettings({
        productName: this.emailProductName().trim() || null,
        logoUrl: this.emailLogoUrl().trim() || null,
        accentColor: this.emailAccentColor().trim() || null,
        supportUrl: this.emailSupportUrl().trim() || null,
        footerNote: this.emailFooterNote().trim() || null,
        welcomeEnabled: this.notifyWelcome(),
        newDeviceEnabled: this.notifyNewDevice(),
        signInNoticeEnabled: this.notifySignIn(),
        magicLinkEnabled: this.magicLinkEnabled(),
        magicLinkRegistrationEnabled: this.magicLinkRegistrationEnabled(),
        magicLinkTtlMinutes: Number(this.magicLinkTtlMinutes()),
      })
      .subscribe({
        next: (res) => {
          this.savingEmail.set(false);
          this.applyEmailSettings(res);
          this.snackbarService.success(
            'Email Settings Saved',
            res.deliveryReady
              ? 'Templates and notifications updated.'
              : 'Saved, but no SMTP relay is configured yet, so nothing will be delivered.',
          );
        },
        error: (err) => {
          this.savingEmail.set(false);
          const msg = err.error?.message || 'Failed to save email template settings.';
          this.emailError.set(msg);
          this.snackbarService.error('Save Failed', msg, err.error?.code);
        },
      });
  }

  sendPreview(type: EmailTemplateType): void {
    const recipient = this.previewRecipient().trim();
    if (!recipient) {
      this.emailError.set('Enter an address to send the preview to.');
      return;
    }

    this.previewSending.set(type);
    this.emailError.set(null);
    this.emailService.sendPreview(type, recipient).subscribe({
      next: (res) => {
        this.previewSending.set(null);
        this.snackbarService.success('Preview Sent', res.message);
      },
      error: (err) => {
        this.previewSending.set(null);
        const msg = err.error?.message || 'Could not send the preview.';
        this.emailError.set(msg);
        this.snackbarService.error('Preview Failed', msg, err.error?.code);
      },
    });
  }

  /** Tailwind classes for a notification's status pill. */
  statusClasses(status: string): string {
    switch (status) {
      case 'ON':
        return 'bg-emerald-100 text-emerald-800';
      case 'NO_RELAY':
        return 'bg-amber-100 text-amber-800';
      default:
        return 'bg-ink-200 text-ink-600';
    }
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'ON':
        return 'On';
      case 'NO_RELAY':
        // Named rather than shown as "On", because an operator who sees "On" beside an
        // undeliverable notification has been told something untrue.
        return 'On · no relay';
      default:
        return 'Off';
    }
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
