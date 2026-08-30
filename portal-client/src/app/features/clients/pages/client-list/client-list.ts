import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Badge } from '../../../../shared/components/badge/badge';
import { OAuthClientService } from '../../services/oauth-client.service';
import { OAuthClient } from '../../models/oauth-client.model';

@Component({
  selector: 'app-client-list',
  imports: [RouterLink, Badge],
  templateUrl: './client-list.html',
})
export class ClientList {
  private readonly clientService = inject(OAuthClientService);

  readonly clients = signal<OAuthClient[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    this.clientService.list().subscribe({
      next: (clients) => {
        this.clients.set(clients);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load OAuth clients.');
        this.loading.set(false);
      },
    });
  }
}
