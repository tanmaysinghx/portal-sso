import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { RegistrationService } from '../../../core/services/registration.service';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.html',
})
export class Register {
  private readonly registrationService = inject(RegistrationService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly policy = this.registrationService.policy;
  readonly policyLoaded = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  /** Set on success; the form is replaced by a confirmation rather than redirecting immediately. */
  readonly done = signal<{ email: string; pendingApproval: boolean } | null>(null);

  readonly form = this.fb.nonNullable.group({
    firstName: [''],
    lastName: [''],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  constructor() {
    // The page is reachable by direct URL, so it re-checks rather than trusting that the sign-in
    // page already did. Also primes the XSRF-TOKEN cookie the POST below needs.
    this.registrationService.loadPolicy().subscribe((policy) => {
      this.policyLoaded.set(true);
      if (!policy.enabled) {
        this.router.navigateByUrl('/sign-in');
      }
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    this.error.set(null);
    this.submitting.set(true);

    this.registrationService
      .register({
        email: value.email.trim(),
        password: value.password,
        firstName: value.firstName.trim() || undefined,
        lastName: value.lastName.trim() || undefined,
      })
      .subscribe({
        next: (result) => {
          this.submitting.set(false);
          this.done.set(result);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          if (err.status === 409) {
            this.error.set('An account with that email already exists.');
          } else if (err.status === 403) {
            this.error.set('Registration is not enabled on this server.');
          } else if (err.status === 400) {
            this.error.set(err.error?.message ?? 'Please check the details below and try again.');
          } else {
            this.error.set('Something went wrong creating your account. Please try again.');
          }
        },
      });
  }

  /**
   * Read from the server rather than duplicated here. The rules are configurable, and a copy in the
   * client would drift and start rejecting passwords the server accepts (or worse, the reverse).
   */
  readonly passwordRules = computed<string[]>(() => {
    const policy = this.registrationService.policy().passwordPolicy;
    if (!policy) {
      return [];
    }
    const rules = [`At least ${policy.minLength} characters`];
    if (policy.requireUppercase) rules.push('An upper-case letter');
    if (policy.requireLowercase) rules.push('A lower-case letter');
    if (policy.requireDigit) rules.push('A digit');
    if (policy.requireSymbol) rules.push('A symbol');
    return rules;
  });
}
