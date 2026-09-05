import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { BrandingService } from '../../../core/services/branding.service';
import { RegistrationService } from '../../../core/services/registration.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
})
export class Login {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly registrationService = inject(RegistrationService);
  readonly brandingService = inject(BrandingService);

  /** Drives the "Create one" link — self-registration is off unless the server says otherwise. */
  readonly registrationPolicy = this.registrationService.policy;

  readonly email = signal('');
  readonly password = signal('');
  readonly rememberMe = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    // This is the SPA's first contact with the backend on a fresh visit — nothing else runs
    // before the user reaches this page. It doubles as priming the XSRF-TOKEN cookie (without
    // it, the login POST below has nothing for the CSRF interceptor to echo back and gets
    // rejected) and as an already-logged-in check.
    this.authService.loadCurrentUser().subscribe((user) => {
      if (user) {
        if (this.authService.isAdmin()) {
          this.router.navigateByUrl('/dashboard');
        } else {
          this.router.navigateByUrl('/apps');
        }
      }
    });

    // Asked rather than assumed: registration is off by default, so linking to /sign-up
    // unconditionally would send most users to a page that immediately bounces them back.
    this.registrationService.loadPolicy().subscribe();
  }

  submit(): void {
    this.error.set(null);
    this.submitting.set(true);

    this.authService.login(this.email(), this.password(), this.rememberMe()).subscribe({
      next: (user) => {
        this.submitting.set(false);
        if (user) {
          if (this.authService.isAdmin()) {
            this.router.navigateByUrl('/dashboard');
          } else {
            this.router.navigateByUrl('/apps');
          }
        } else {
          this.error.set('Invalid email or password.');
        }
      },
      error: () => {
        this.submitting.set(false);
        this.error.set('Invalid email or password.');
      },
    });
  }
}
