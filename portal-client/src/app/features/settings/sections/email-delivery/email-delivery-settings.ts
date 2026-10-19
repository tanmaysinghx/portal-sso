import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SnackbarService } from '../../../../core/services/snackbar.service';
import {
  SendTestEmailResponse,
  SmtpService,
  SmtpSettingsResponse,
  SmtpTransportSecurity,
  UpdateSmtpSettingsRequest,
} from '../../../../core/services/smtp.service';

/** How mail leaves the server: the SMTP relay, stored in the database and editable without a restart. */
@Component({
  selector: 'app-email-delivery-settings',
  standalone: true,
  imports: [DatePipe, FormsModule],
  templateUrl: './email-delivery-settings.html',
})
export class EmailDeliverySettings {
  private readonly smtpService = inject(SmtpService);
  private readonly snackbarService = inject(SnackbarService);

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

  constructor() {
    this.loadSmtpSettings();
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
}
