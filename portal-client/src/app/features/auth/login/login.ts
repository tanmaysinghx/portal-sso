import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
})
export class Login {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

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
      if (user && this.authService.isAdmin()) {
        this.router.navigateByUrl('/dashboard');
      }
    });
  }

  submit(): void {
    this.error.set(null);
    this.submitting.set(true);

    this.authService.login(this.email(), this.password(), this.rememberMe()).subscribe({
      next: (user) => {
        this.submitting.set(false);
        if (user) {
          this.router.navigateByUrl('/dashboard');
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
