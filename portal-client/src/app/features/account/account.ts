import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Account as AccountModel, AccountService, KnownDevice, passwordChecklist } from '../../core/services/account.service';
import { ADMIN_ROLE } from '../../core/models/current-user.model';
import { MfaClientService } from '../../core/services/mfa.service';
import { SnackbarService } from '../../core/services/snackbar.service';
import { generateQrCodeSvg } from '../../core/utils/qr-code';
import { Badge } from '../../shared/components/badge/badge';

type AccountSection = 'profile' | 'password' | 'two-factor' | 'devices';

/**
 * My Account: everything a signed-in user manages about themselves. Available to every role —
 * personal two-factor used to live under the admin-only Settings page, which left ordinary users
 * with no way to turn it on.
 */
@Component({
  selector: 'app-account',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink, Badge],
  templateUrl: './account.html',
})
export class Account implements OnInit {
  private readonly accountService = inject(AccountService);
  private readonly mfaClientService = inject(MfaClientService);
  private readonly snackbar = inject(SnackbarService);

  readonly sections: { id: AccountSection; label: string }[] = [
    { id: 'profile', label: 'Profile' },
    { id: 'password', label: 'Password' },
    { id: 'two-factor', label: 'Two-factor authentication' },
    { id: 'devices', label: 'Devices & sessions' },
  ];

  readonly account = signal<AccountModel | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);

  readonly displayName = computed(() => {
    const a = this.account();
    if (!a) return '';
    const name = [a.firstName, a.lastName].filter(Boolean).join(' ');
    return name || a.email;
  });
  readonly initials = computed(() => {
    const a = this.account();
    if (!a) return '?';
    const fromName = [a.firstName, a.lastName].filter(Boolean).map((p) => p!.charAt(0)).join('');
    return (fromName || a.email.charAt(0)).toUpperCase().slice(0, 2);
  });
  readonly roleLabels = computed(() =>
    (this.account()?.roles ?? []).map((r) => (r === ADMIN_ROLE ? 'Administrator' : r.replace(/^ROLE_/, '').toLowerCase())),
  );

  // Profile
  readonly firstName = signal('');
  readonly lastName = signal('');
  readonly savingProfile = signal(false);
  readonly profileDirty = computed(() => {
    const a = this.account();
    return !!a && (this.firstName().trim() !== (a.firstName ?? '') || this.lastName().trim() !== (a.lastName ?? ''));
  });

  // Password
  readonly currentPassword = signal('');
  readonly newPassword = signal('');
  readonly confirmPassword = signal('');
  readonly showPasswords = signal(false);
  readonly changingPassword = signal(false);
  readonly passwordError = signal<string | null>(null);
  readonly checklist = computed(() => {
    const policy = this.account()?.passwordPolicy;
    return policy ? passwordChecklist(policy, this.newPassword()) : [];
  });
  readonly passwordsMatch = computed(() => this.confirmPassword().length > 0 && this.newPassword() === this.confirmPassword());
  readonly canChangePassword = computed(
    () =>
      this.currentPassword().length > 0 &&
      this.checklist().every((r) => r.met) &&
      this.passwordsMatch() &&
      !this.changingPassword(),
  );

  // Two-factor enrolment
  readonly showMfaModal = signal(false);
  readonly mfaStep = signal<'setup' | 'recovery'>('setup');
  readonly mfaSecret = signal('');
  readonly mfaQrUrl = signal('');
  readonly mfaCode = signal('');
  readonly mfaError = signal<string | null>(null);
  readonly mfaSubmitting = signal(false);
  readonly recoveryCodes = signal<string[]>([]);
  readonly recoveryCodesCopied = signal(false);

  // Two-factor disable
  readonly showDisableModal = signal(false);
  readonly disablePassword = signal('');
  readonly disableError = signal<string | null>(null);
  readonly disableSubmitting = signal(false);

  // Recovery code regeneration
  readonly showRegenerateModal = signal(false);
  readonly regenerateCode = signal('');
  readonly regenerateError = signal<string | null>(null);
  readonly regenerating = signal(false);

  // Devices
  readonly devices = signal<KnownDevice[]>([]);
  readonly loadingDevices = signal(true);
  readonly deviceToForget = signal<KnownDevice | null>(null);
  readonly forgettingDevice = signal(false);

  ngOnInit(): void {
    this.loadAccount();
    this.loadDevices();
  }

  loadAccount(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.accountService.get().subscribe({
      next: (account) => {
        this.applyAccount(account);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.loadError.set(err.error?.message || 'Could not load your account.');
      },
    });
  }

  private applyAccount(account: AccountModel): void {
    this.account.set(account);
    this.firstName.set(account.firstName ?? '');
    this.lastName.set(account.lastName ?? '');
  }

  scrollTo(section: AccountSection): void {
    document.getElementById(`account-${section}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  // ---- Profile ----

  saveProfile(): void {
    this.savingProfile.set(true);
    this.accountService.updateProfile(this.firstName().trim(), this.lastName().trim()).subscribe({
      next: (account) => {
        this.savingProfile.set(false);
        this.applyAccount(account);
        this.snackbar.success('Profile updated');
      },
      error: (err) => {
        this.savingProfile.set(false);
        this.snackbar.error('Could not update profile', err.error?.message, err.error?.code);
      },
    });
  }

  resetProfile(): void {
    const a = this.account();
    this.firstName.set(a?.firstName ?? '');
    this.lastName.set(a?.lastName ?? '');
  }

  // ---- Password ----

  changePassword(): void {
    if (!this.canChangePassword()) return;
    this.changingPassword.set(true);
    this.passwordError.set(null);
    this.accountService.changePassword(this.currentPassword(), this.newPassword()).subscribe({
      next: (res) => {
        this.changingPassword.set(false);
        this.currentPassword.set('');
        this.newPassword.set('');
        this.confirmPassword.set('');
        const n = res.otherSessionsSignedOut;
        this.snackbar.success(
          'Password changed',
          n > 0 ? `Signed out ${n} other session${n === 1 ? '' : 's'}.` : 'You stay signed in on this device.',
        );
      },
      error: (err) => {
        this.changingPassword.set(false);
        this.passwordError.set(err.error?.message || 'Could not change your password.');
      },
    });
  }

  // ---- Two-factor ----

  startMfaEnrollment(): void {
    this.mfaSubmitting.set(true);
    this.mfaError.set(null);
    this.mfaCode.set('');
    this.mfaStep.set('setup');
    this.mfaClientService.setup().subscribe({
      next: (setup) => {
        this.mfaSecret.set(setup.secret);
        this.mfaQrUrl.set(generateQrCodeSvg(setup.provisioningUri));
        this.mfaSubmitting.set(false);
        this.showMfaModal.set(true);
      },
      error: (err) => {
        this.mfaSubmitting.set(false);
        this.snackbar.error('Could not start setup', err.error?.message, err.error?.code);
      },
    });
  }

  confirmMfa(): void {
    const code = this.mfaCode().trim();
    if (!/^\d{6}$/.test(code)) {
      this.mfaError.set('Enter the 6-digit code from your authenticator app.');
      return;
    }
    this.mfaSubmitting.set(true);
    this.mfaError.set(null);
    this.mfaClientService.confirm(code).subscribe({
      next: (res) => {
        this.mfaSubmitting.set(false);
        this.showRecoveryCodes(res.recoveryCodes);
        this.snackbar.success('Two-factor authentication is on');
      },
      error: (err) => {
        this.mfaSubmitting.set(false);
        this.mfaError.set(err.error?.message || 'That code did not match. Check your app and try again.');
      },
    });
  }

  private showRecoveryCodes(codes: string[]): void {
    this.recoveryCodes.set(codes);
    this.recoveryCodesCopied.set(false);
    this.mfaStep.set('recovery');
    this.showMfaModal.set(true);
  }

  copyRecoveryCodes(): void {
    navigator.clipboard.writeText(this.recoveryCodes().join('\n')).then(() => {
      this.recoveryCodesCopied.set(true);
      setTimeout(() => this.recoveryCodesCopied.set(false), 3000);
    });
  }

  downloadRecoveryCodes(): void {
    const body = `Portal SSO recovery codes for ${this.account()?.email}\nEach code works once.\n\n${this.recoveryCodes().join('\n')}\n`;
    const url = URL.createObjectURL(new Blob([body], { type: 'text/plain' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'portal-sso-recovery-codes.txt';
    link.click();
    URL.revokeObjectURL(url);
  }

  closeMfaModal(): void {
    this.showMfaModal.set(false);
    // Codes are shown once; drop them from memory as soon as the dialog closes.
    this.recoveryCodes.set([]);
    this.mfaSecret.set('');
    this.loadAccount();
  }

  openDisableModal(): void {
    this.disablePassword.set('');
    this.disableError.set(null);
    this.showDisableModal.set(true);
  }

  disableMfa(): void {
    if (!this.disablePassword()) return;
    this.disableSubmitting.set(true);
    this.disableError.set(null);
    this.mfaClientService.disable(this.disablePassword()).subscribe({
      next: () => {
        this.disableSubmitting.set(false);
        this.showDisableModal.set(false);
        this.snackbar.info('Two-factor authentication is off');
        this.loadAccount();
      },
      error: (err) => {
        this.disableSubmitting.set(false);
        this.disableError.set(err.error?.message || 'Incorrect password.');
      },
    });
  }

  openRegenerateModal(): void {
    this.regenerateCode.set('');
    this.regenerateError.set(null);
    this.showRegenerateModal.set(true);
  }

  regenerateRecoveryCodes(): void {
    if (!this.regenerateCode().trim()) return;
    this.regenerating.set(true);
    this.regenerateError.set(null);
    this.accountService.regenerateRecoveryCodes(this.regenerateCode().trim()).subscribe({
      next: (res) => {
        this.regenerating.set(false);
        this.showRegenerateModal.set(false);
        this.showRecoveryCodes(res.recoveryCodes);
      },
      error: (err) => {
        this.regenerating.set(false);
        this.regenerateError.set(err.error?.message || 'That code is not valid.');
      },
    });
  }

  // ---- Devices ----

  loadDevices(): void {
    this.loadingDevices.set(true);
    this.accountService.getDevices().subscribe({
      next: (devices) => {
        this.devices.set(devices);
        this.loadingDevices.set(false);
      },
      error: () => this.loadingDevices.set(false),
    });
  }

  confirmForgetDevice(): void {
    const device = this.deviceToForget();
    if (!device) return;
    this.forgettingDevice.set(true);
    this.accountService.forgetDevice(device.id).subscribe({
      next: () => {
        this.forgettingDevice.set(false);
        this.deviceToForget.set(null);
        this.devices.update((list) => list.filter((d) => d.id !== device.id));
        this.snackbar.success('Device removed', 'Its next sign-in will trigger a new-device alert.');
      },
      error: (err) => {
        this.forgettingDevice.set(false);
        this.snackbar.error('Could not remove device', err.error?.message, err.error?.code);
      },
    });
  }
}
