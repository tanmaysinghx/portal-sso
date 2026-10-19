import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EmailService, EmailSettingsResponse, EmailTemplateType } from '../../../../core/services/email.service';
import { SnackbarService } from '../../../../core/services/snackbar.service';

/** What is sent: email branding, which notifications fire, magic-link policy and previews. */
@Component({
  selector: 'app-email-templates-settings',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './email-templates-settings.html',
})
export class EmailTemplatesSettings {
  private readonly emailService = inject(EmailService);
  private readonly snackbarService = inject(SnackbarService);

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

  constructor() {
    this.loadEmailSettings();
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
}
