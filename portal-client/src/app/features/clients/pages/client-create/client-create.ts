import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { OAuthClientService } from '../../services/oauth-client.service';

const INPUT_BASE_CLASSES =
  'block w-full rounded-lg border px-3.5 py-2.5 text-sm text-ink-900 shadow-sm transition-colors placeholder:text-ink-400 focus:outline-none focus:ring-2';
const INPUT_VALID_CLASSES = 'border-ink-300 focus:border-brand-500 focus:ring-brand-500/25';
const INPUT_INVALID_CLASSES = 'border-red-400 focus:border-red-400 focus:ring-red-400/25';

@Component({
  selector: 'app-client-create',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './client-create.html',
})
export class ClientCreate {
  private readonly fb = inject(FormBuilder);
  private readonly clientService = inject(OAuthClientService);
  private readonly router = inject(Router);

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  /**
   * Set only after a confidential client is created. The server hashes the secret and has no
   * endpoint that can show it again, so navigating away here would lose it permanently — which is
   * exactly what this screen did before confidential clients existed.
   */
  readonly issuedSecret = signal<string | null>(null);
  readonly issuedClientId = signal<string | null>(null);
  readonly secretCopied = signal(false);

  readonly form = this.fb.nonNullable.group({
    clientId: ['', [Validators.required, Validators.pattern(/^[a-zA-Z0-9._-]+$/)]],
    clientName: ['', Validators.required],
    redirectUris: ['', Validators.required],
    profileScope: [true],
    emailScope: [true],
    confidential: [false],
  });

  copySecret(): void {
    const secret = this.issuedSecret();
    if (!secret) {
      return;
    }
    navigator.clipboard.writeText(secret).then(
      () => {
        this.secretCopied.set(true);
        setTimeout(() => this.secretCopied.set(false), 2000);
      },
      // Clipboard access can be denied; the secret is selectable on screen either way.
      () => this.secretCopied.set(false),
    );
  }

  done(): void {
    this.router.navigateByUrl('/clients');
  }

  clientIdClasses(): string {
    const control = this.form.controls.clientId;
    const invalid = control.touched && control.invalid;
    return `${INPUT_BASE_CLASSES} ${invalid ? INPUT_INVALID_CLASSES : INPUT_VALID_CLASSES}`;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    const redirectUris = value.redirectUris
      .split('\n')
      .map((uri) => uri.trim())
      .filter((uri) => uri.length > 0);

    if (redirectUris.length === 0) {
      this.error.set('At least one redirect URI is required.');
      return;
    }

    const scopes = ['openid', ...(value.profileScope ? ['profile'] : []), ...(value.emailScope ? ['email'] : [])];

    this.error.set(null);
    this.submitting.set(true);

    this.clientService
      .create({
        clientId: value.clientId,
        clientName: value.clientName,
        redirectUris,
        scopes,
        confidential: value.confidential,
      })
      .subscribe({
        next: (created) => {
          if (created.clientSecret) {
            // Stay put and show it once.
            this.submitting.set(false);
            this.issuedClientId.set(created.client.clientId);
            this.issuedSecret.set(created.clientSecret);
            return;
          }
          this.router.navigateByUrl('/clients');
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          if (err.status === 409) {
            this.error.set('A client with that Client ID already exists.');
          } else if (err.status === 400) {
            this.error.set('Please check the fields below and try again.');
          } else {
            this.error.set('Something went wrong creating the client.');
          }
        },
      });
  }
}
