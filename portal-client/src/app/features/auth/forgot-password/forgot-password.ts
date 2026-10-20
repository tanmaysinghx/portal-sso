import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PasswordResetService } from '../../../core/services/password-reset.service';
import { ThemeService } from '../../../core/services/theme.service';

/** "Forgot password?": asks for an address and says the same thing whatever happens next. */
@Component({
  selector: 'app-forgot-password',
  imports: [FormsModule, RouterLink],
  templateUrl: './forgot-password.html',
})
export class ForgotPassword {
  private readonly passwordResetService = inject(PasswordResetService);
  readonly themeService = inject(ThemeService);

  /**
   * Arrived from the server-rendered sign-in page (mid OAuth sign-in): "back" must return there so
   * the saved authorization request in the session is resumed, not to the console's sign-in.
   */
  readonly fromServerLogin = inject(ActivatedRoute).snapshot.queryParamMap.get('from') === 'login';

  readonly available = this.passwordResetService.available;
  readonly policyLoaded = signal(false);
  readonly email = signal('');
  readonly sending = signal(false);
  readonly sentMessage = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  constructor() {
    this.passwordResetService.loadPolicy().subscribe({
      next: () => this.policyLoaded.set(true),
      error: () => this.policyLoaded.set(true),
    });
  }

  submit(): void {
    const email = this.email().trim();
    if (!email) {
      this.error.set('Enter the email address you sign in with.');
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    this.passwordResetService.request(email).subscribe({
      next: (res) => {
        this.sending.set(false);
        this.sentMessage.set(res.message);
      },
      error: (err) => {
        this.sending.set(false);
        this.error.set(
          err.status === 429
            ? 'Too many requests from this network. Wait a minute and try again.'
            : err.error?.message || 'Something went wrong. Try again in a moment.',
        );
      },
    });
  }
}
