import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { BrandingService } from '../../../core/services/branding.service';
import { RegistrationService } from '../../../core/services/registration.service';
import { SetupService } from '../../../core/services/setup.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
})
export class Login {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly registrationService = inject(RegistrationService);
  private readonly setupService = inject(SetupService);
  readonly brandingService = inject(BrandingService);

  /** Drives the "Create one" link — self-registration is off unless the server says otherwise. */
  readonly registrationPolicy = this.registrationService.policy;

  readonly email = signal('');
  readonly password = signal('');
  readonly confirmPassword = signal('');
  readonly firstName = signal('');
  readonly lastName = signal('');
  readonly rememberMe = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  /** First-Run Setup Wizard state */
  readonly setupRequired = signal(false);
  readonly setupLoading = signal(true);
  readonly databaseType = signal('');

  constructor() {
    this.authService.loadCurrentUser().subscribe((user) => {
      if (user) {
        if (this.authService.isAdmin()) {
          this.router.navigateByUrl('/dashboard');
        } else {
          this.router.navigateByUrl('/apps');
        }
      }
    });

    this.setupService.getStatus().subscribe({
      next: (status) => {
        this.setupRequired.set(status.setupRequired);
        this.databaseType.set(status.databaseType);
        this.setupLoading.set(false);
      },
      error: () => this.setupLoading.set(false),
    });

    this.registrationService.loadPolicy().subscribe();
  }

  submit(): void {
    this.error.set(null);
    this.successMessage.set(null);
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

  submitSetup(): void {
    this.error.set(null);
    this.successMessage.set(null);

    if (!this.email() || !this.password()) {
      this.error.set('Email and password are required.');
      return;
    }
    if (this.password() !== this.confirmPassword()) {
      this.error.set('Passwords do not match.');
      return;
    }
    if (this.password().length < 12) {
      this.error.set('Administrator password must be at least 12 characters.');
      return;
    }

    this.submitting.set(true);
    this.setupService.initialize({
      email: this.email(),
      password: this.password(),
      firstName: this.firstName(),
      lastName: this.lastName(),
    }).subscribe({
      next: () => {
        this.authService.login(this.email(), this.password(), true).subscribe({
          next: (user) => {
            this.submitting.set(false);
            if (user) {
              this.router.navigateByUrl('/dashboard');
            } else {
              this.setupRequired.set(false);
              this.successMessage.set('Administrator account configured successfully! Please sign in.');
            }
          },
          error: () => {
            this.submitting.set(false);
            this.setupRequired.set(false);
            this.successMessage.set('Administrator account configured successfully! Please sign in.');
          },
        });
      },
      error: (err) => {
        this.submitting.set(false);
        const msg = err.error?.message || err.error?.error || 'Setup initialization failed. Please verify password requirements and try again.';
        this.error.set(msg);
      },
    });
  }
}
