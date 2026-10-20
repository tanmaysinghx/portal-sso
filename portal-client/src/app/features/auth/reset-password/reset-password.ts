import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { PasswordPolicy, passwordChecklist } from '../../../core/services/account.service';
import { PasswordResetService } from '../../../core/services/password-reset.service';
import { ThemeService } from '../../../core/services/theme.service';

type ResetState = 'checking' | 'ready' | 'unusable' | 'done';

/** Where the emailed link lands: checks the link first, then takes the new password. */
@Component({
  selector: 'app-reset-password',
  imports: [FormsModule, RouterLink],
  templateUrl: './reset-password.html',
})
export class ResetPassword {
  private readonly passwordResetService = inject(PasswordResetService);
  private readonly router = inject(Router);
  readonly themeService = inject(ThemeService);

  /** Held in memory only; see the constructor. */
  private readonly token: string;

  readonly state = signal<ResetState>('checking');
  readonly unusableReason = signal('');
  readonly email = signal('');
  readonly policy = signal<PasswordPolicy | null>(null);

  readonly newPassword = signal('');
  readonly confirmPassword = signal('');
  readonly showPasswords = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  readonly checklist = computed(() => {
    const policy = this.policy();
    return policy ? passwordChecklist(policy, this.newPassword()) : [];
  });
  readonly passwordsMatch = computed(() => this.confirmPassword().length > 0 && this.newPassword() === this.confirmPassword());
  readonly canSubmit = computed(
    () => this.checklist().every((r) => r.met) && this.passwordsMatch() && !this.submitting(),
  );

  constructor() {
    this.token = inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? '';
    // Out of the address bar straight away: a URL holding a live credential ends up in history,
    // bookmarks, screenshots and screen shares. It is still in the emailed link if needed again.
    this.router.navigate([], { queryParams: { token: null }, queryParamsHandling: 'merge', replaceUrl: true });

    if (!this.token) {
      this.markUnusable('This link is incomplete. Open it again from the email, or request a new one.');
      return;
    }
    // The GET comes first because it is what issues the XSRF-TOKEN cookie. Someone arriving
    // straight from their mailbox has made no request yet, and a POST without that cookie is
    // refused by CSRF protection before it reaches the reset endpoint.
    this.passwordResetService
      .loadPolicy()
      .pipe(switchMap(() => this.passwordResetService.inspect(this.token)))
      .subscribe({
        next: (res) => {
          this.email.set(res.email);
          this.policy.set(res.passwordPolicy);
          this.state.set('ready');
        },
        error: (err) =>
          this.markUnusable(
            err.error?.message ||
              "We couldn't check this link just now. Reload the page to try again, or request a new link.",
          ),
      });
  }

  submit(): void {
    if (!this.canSubmit()) return;
    this.submitting.set(true);
    this.error.set(null);
    this.passwordResetService.confirm(this.token, this.newPassword()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.newPassword.set('');
        this.confirmPassword.set('');
        this.state.set('done');
      },
      error: (err) => {
        this.submitting.set(false);
        // Expired or used while the form was open: the form can no longer succeed.
        if (err.status === 410 || err.error?.code === 'PRTL-4014') {
          this.markUnusable(err.error?.message || 'This link can no longer be used.');
          return;
        }
        this.error.set(err.error?.message || 'Could not change the password. Try again.');
      },
    });
  }

  private markUnusable(reason: string): void {
    this.unusableReason.set(reason);
    this.state.set('unusable');
  }
}
