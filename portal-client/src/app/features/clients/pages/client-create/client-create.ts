import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { buildEnvFile } from '../../../applications/integration-snippets';
import { OAuthClientService } from '../../services/oauth-client.service';

const INPUT_BASE_CLASSES =
  'block w-full rounded-lg border px-3.5 py-2.5 text-sm text-ink-900 shadow-sm transition-colors placeholder:text-ink-400 focus:outline-none focus:ring-2';
const INPUT_VALID_CLASSES = 'border-ink-300 focus:border-brand-500 focus:ring-brand-500/25';
const INPUT_INVALID_CLASSES = 'border-red-400 focus:border-red-400 focus:ring-red-400/25';

export interface CreatedClientSummary {
  clientId: string;
  clientName: string;
  clientSecret?: string;
  issuerUrl: string;
  redirectUris: string[];
  scopes: string[];
  envSnippet: string;
}

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

  readonly createdSummary = signal<CreatedClientSummary | null>(null);
  readonly secretCopied = signal(false);
  readonly clientIdCopied = signal(false);
  readonly envCopied = signal(false);

  readonly form = this.fb.nonNullable.group({
    clientId: ['', [Validators.required, Validators.pattern(/^[a-zA-Z0-9._-]+$/)]],
    clientName: ['', Validators.required],
    redirectUris: ['', Validators.required],
    profileScope: [true],
    emailScope: [true],
    confidential: [false],
  });

  copySecret(): void {
    const secret = this.createdSummary()?.clientSecret;
    if (!secret) return;
    navigator.clipboard.writeText(secret).then(() => {
      this.secretCopied.set(true);
      setTimeout(() => this.secretCopied.set(false), 2000);
    });
  }

  copyClientId(): void {
    const cid = this.createdSummary()?.clientId;
    if (!cid) return;
    navigator.clipboard.writeText(cid).then(() => {
      this.clientIdCopied.set(true);
      setTimeout(() => this.clientIdCopied.set(false), 2000);
    });
  }

  copyEnv(): void {
    const env = this.createdSummary()?.envSnippet;
    if (!env) return;
    navigator.clipboard.writeText(env).then(() => {
      this.envCopied.set(true);
      setTimeout(() => this.envCopied.set(false), 2000);
    });
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
          this.submitting.set(false);
          // The console is served by the authorization server itself, so its origin is the issuer.
          const issuerUrl = window.location.origin;
          const envSnippet = buildEnvFile(created.client.clientName, {
            issuerUrl,
            discoveryUrl: `${issuerUrl}/.well-known/openid-configuration`,
            oidc: {
              clientId: created.client.clientId,
              clientSecret: created.clientSecret,
              confidential: created.client.confidential,
              redirectUris: created.client.redirectUris,
              scopes: created.client.scopes,
              popupCallbackUri: `${issuerUrl}/oauth2/popup-callback`,
              clientExists: true,
            },
            externalAuth: null,
          });

          this.createdSummary.set({
            clientId: created.client.clientId,
            clientName: created.client.clientName,
            clientSecret: created.clientSecret || undefined,
            issuerUrl,
            redirectUris: created.client.redirectUris,
            scopes: created.client.scopes,
            envSnippet,
          });
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
