import { HttpErrorResponse } from '@angular/common/http';
import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { Subject, debounceTime } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { SnackbarService } from '../../../../core/services/snackbar.service';
import { Badge } from '../../../../shared/components/badge/badge';
import { OAuthClientService } from '../../services/oauth-client.service';
import { OAuthClient } from '../../models/oauth-client.model';

const PAGE_SIZE = 25;

const INPUT_CLASSES =
  'block w-full rounded-lg border border-ink-300 px-3.5 py-2.5 text-sm text-ink-900 shadow-sm transition-colors placeholder:text-ink-400 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/25';

@Component({
  selector: 'app-client-list',
  imports: [RouterLink, Badge, ReactiveFormsModule],
  templateUrl: './client-list.html',
})
export class ClientList {
  private readonly clientService = inject(OAuthClientService);
  private readonly snackbar = inject(SnackbarService);
  private readonly fb = inject(FormBuilder);

  readonly inputClasses = INPUT_CLASSES;

  readonly clients = signal<OAuthClient[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  // Paging and filters. The registry used to fetch and render every client at once.
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly searchTerm = signal('');
  readonly enabledFilter = signal<boolean | null>(null);
  private readonly searchInput = new Subject<string>();
  private latestRequest = 0;

  readonly enabledFilterValue = computed(() =>
    this.enabledFilter() === null ? '' : String(this.enabledFilter()),
  );
  readonly hasFilters = computed(() => this.searchTerm() !== '' || this.enabledFilter() !== null);
  readonly rangeLabel = computed(() => {
    const total = this.totalElements();
    if (total === 0) {
      return 'No clients';
    }
    const first = this.page() * PAGE_SIZE + 1;
    return `${first}\u2013${Math.min(first + this.clients().length - 1, total)} of ${total}`;
  });

  readonly editing = signal<OAuthClient | null>(null);
  readonly deleting = signal<OAuthClient | null>(null);
  readonly submitting = signal(false);
  readonly modalError = signal<string | null>(null);

  readonly editForm = this.fb.nonNullable.group({
    clientName: ['', Validators.required],
    redirectUris: ['', Validators.required],
    profileScope: [false],
    emailScope: [false],
    enabled: [true],
  });

  private readonly route = inject(ActivatedRoute);

  constructor() {
    // The command palette links here with ?search=…; without this the list would open unfiltered
    // and quietly ignore what the user just typed.
    const initial = this.route.snapshot.queryParamMap.get('search');
    if (initial) {
      this.searchTerm.set(initial);
    }

    // Debounced so typing does not fire a query per keystroke; this subscription is the only thing
    // that applies a search change, so a value pushed here supersedes any pending keystroke.
    this.searchInput.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((value) => {
      this.searchTerm.set(value);
      this.page.set(0);
      this.load();
    });

    this.load();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeModals();
  }

  load(): void {
    this.loading.set(true);

    // Only the newest request may write: responses are not guaranteed to arrive in request order.
    const request = ++this.latestRequest;

    this.clientService.list(this.searchTerm(), this.enabledFilter(), this.page(), PAGE_SIZE).subscribe({
      next: (result) => {
        if (request !== this.latestRequest) {
          return;
        }
        this.clients.set(result.content);
        this.totalPages.set(result.totalPages);
        this.totalElements.set(result.totalElements);
        this.loading.set(false);
      },
      error: () => {
        if (request !== this.latestRequest) {
          return;
        }
        this.error.set('Could not load OAuth clients.');
        this.loading.set(false);
      },
    });
  }

  onSearchInput(value: string): void {
    this.searchInput.next(value);
  }

  onEnabledFilterChange(value: string): void {
    this.enabledFilter.set(value === '' ? null : value === 'true');
    this.page.set(0);
    this.load();
  }

  clearFilters(): void {
    this.enabledFilter.set(null);
    // Through the debounced stream so a keystroke in flight cannot re-apply the cleared search.
    this.searchInput.next('');
  }

  goToPage(target: number): void {
    if (target < 0 || (this.totalPages() > 0 && target >= this.totalPages())) {
      return;
    }
    this.page.set(target);
    this.load();
  }

  openEdit(client: OAuthClient): void {
    this.modalError.set(null);
    this.editForm.setValue({
      clientName: client.clientName,
      redirectUris: client.redirectUris.join('\n'),
      // openid is always sent and is not offered as a choice, matching the create form.
      profileScope: client.scopes.includes('profile'),
      emailScope: client.scopes.includes('email'),
      enabled: client.enabled,
    });
    this.editing.set(client);
  }

  openDelete(client: OAuthClient): void {
    this.modalError.set(null);
    this.deleting.set(client);
  }

  closeModals(): void {
    this.editing.set(null);
    this.deleting.set(null);
    this.submitting.set(false);
    this.modalError.set(null);
  }

  saveEdit(): void {
    const client = this.editing();
    if (!client || this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }

    const value = this.editForm.getRawValue();
    const redirectUris = value.redirectUris
      .split('\n')
      .map((uri) => uri.trim())
      .filter((uri) => uri.length > 0);

    if (redirectUris.length === 0) {
      this.modalError.set('At least one redirect URI is required.');
      return;
    }

    const scopes = [
      'openid',
      ...(value.profileScope ? ['profile'] : []),
      ...(value.emailScope ? ['email'] : []),
    ];

    this.modalError.set(null);
    this.submitting.set(true);

    this.clientService
      .update(client.id, {
        clientName: value.clientName,
        redirectUris,
        scopes,
        enabled: value.enabled,
      })
      .subscribe({
        next: (updated) => {
          this.clients.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
          this.closeModals();
          this.snackbar.info('Client Updated', `${updated.clientName} has been saved.`);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.modalError.set(err.error?.message ?? 'Could not save that client — please try again.');
        },
      });
  }

  confirmDelete(): void {
    const client = this.deleting();
    if (!client) {
      return;
    }

    this.modalError.set(null);
    this.submitting.set(true);

    this.clientService.delete(client.id).subscribe({
      next: () => {
        this.clients.update((list) => list.filter((c) => c.id !== client.id));
        this.closeModals();
        this.snackbar.info('Client Deleted', `${client.clientName} and its tokens were removed.`);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.modalError.set(err.error?.message ?? 'Could not delete that client — please try again.');
      },
    });
  }
}
