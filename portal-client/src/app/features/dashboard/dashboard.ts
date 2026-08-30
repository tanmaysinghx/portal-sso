import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { OAuthClientService } from '../clients/services/oauth-client.service';
import { UserService } from '../users/services/user.service';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  templateUrl: './dashboard.html',
})
export class Dashboard {
  private readonly authService = inject(AuthService);
  private readonly clientService = inject(OAuthClientService);
  private readonly userService = inject(UserService);

  readonly currentUser = this.authService.currentUser;

  /**
   * Fixed by Spring Authorization Server's endpoint configuration on the server side, so these
   * paths hold for any deployment — only the origin (the configured ISSUER_URL) varies.
   */
  readonly endpoints = [
    { label: 'Discovery', path: '/.well-known/openid-configuration' },
    { label: 'Authorization', path: '/oauth2/authorize' },
    { label: 'Token', path: '/oauth2/token' },
    { label: 'JWKS', path: '/oauth2/jwks' },
    { label: 'User info', path: '/userinfo' },
  ];

  readonly clientCount = signal<number | null>(null);
  readonly userCount = signal<number | null>(null);
  readonly activeUserCount = signal<number | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    // forkJoin rather than two independent subscribes: the stats are one logical unit, so they
    // should flip out of the loading state together instead of popping in one at a time (very
    // visible against a remote database, where each call can take ~1s).
    forkJoin({
      clients: this.clientService.list(),
      users: this.userService.list(),
    }).subscribe({
      next: ({ clients, users }) => {
        this.clientCount.set(clients.length);
        this.userCount.set(users.length);
        this.activeUserCount.set(users.filter((u) => u.enabled).length);
        this.loading.set(false);
      },
      error: () => {
        // Without this the counts would sit on their placeholder forever, indistinguishable from
        // "still loading" — the reason a failure here used to be invisible.
        this.error.set('Could not load dashboard stats.');
        this.loading.set(false);
      },
    });
  }
}
