import { Component, ElementRef, HostListener, computed, inject, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, switchMap, of, catchError } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { UserService } from '../../features/users/services/user.service';
import { OAuthClientService } from '../../features/clients/services/oauth-client.service';
import { SnackbarService } from '../../core/services/snackbar.service';

export interface SearchItem {
  id: string;
  title: string;
  category: 'Navigation' | 'Action' | 'Resource';
  description: string;
  route?: string;
  action?: () => void;
  icon: string;
}

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, FormsModule],
  templateUrl: './header.html',
  host: {
    class: 'sticky top-0 z-30 block',
  },
})
export class Header {
  /** Opens the off-canvas navigation on phone and tablet. Ignored above lg, where it is hidden. */
  readonly menuToggle = output<void>();

  private readonly router = inject(Router);
  private readonly snackbarService = inject(SnackbarService);
  private readonly elementRef = inject(ElementRef);

  readonly searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  readonly isDropdownOpen = signal(false);
  readonly searchQuery = signal('');

  readonly searchItems: SearchItem[] = [
    {
      id: 'nav-dash',
      title: 'Dashboard',
      category: 'Navigation',
      description: 'System overview and live authentication metrics',
      route: '/dashboard',
      icon: 'dashboard',
    },
    {
      id: 'nav-clients',
      title: 'OAuth Clients',
      category: 'Navigation',
      description: 'View registered OAuth 2.1 & OIDC relying parties',
      route: '/clients',
      icon: 'clients',
    },
    {
      id: 'nav-create-client',
      title: 'Register New OAuth Client',
      category: 'Action',
      description: 'Register a public PKCE or confidential client application',
      route: '/clients/new',
      icon: 'plus',
    },
    {
      id: 'nav-users',
      title: 'User Directory',
      category: 'Navigation',
      description: 'Manage users, role permissions, and access status',
      route: '/users',
      icon: 'users',
    },
    {
      id: 'nav-settings',
      title: 'Settings & Branding',
      category: 'Navigation',
      description: 'Configure company logo, token lifecycles, and security keys',
      route: '/settings',
      icon: 'settings',
    },
    {
      id: 'act-copy-discovery',
      title: 'Copy OpenID Discovery URL',
      category: 'Action',
      description: 'Copy /.well-known/openid-configuration to clipboard',
      action: () => {
        const url = `${window.location.origin}/.well-known/openid-configuration`;
        navigator.clipboard.writeText(url);
        this.snackbarService.success('URL Copied', `Copied ${url} to clipboard.`);
      },
      icon: 'link',
    },
    {
      id: 'act-copy-jwks',
      title: 'Copy JWKS Key URL',
      category: 'Action',
      description: 'Copy /oauth2/jwks endpoint URL to clipboard',
      action: () => {
        const url = `${window.location.origin}/oauth2/jwks`;
        navigator.clipboard.writeText(url);
        this.snackbarService.success('URL Copied', `Copied ${url} to clipboard.`);
      },
      icon: 'key',
    },
  ];

  readonly filteredSearchItems = computed(() => {
    const q = this.searchQuery().trim().toLowerCase();
    if (!q) {
      return this.searchItems;
    }
    return this.searchItems.filter(
      (item) =>
        item.title.toLowerCase().includes(q) ||
        item.description.toLowerCase().includes(q) ||
        item.category.toLowerCase().includes(q)
    );
  });

  /**
   * Live results from the API, alongside the static destinations above.
   *
   * <p>The placeholder has always said "Search clients, users, settings…" while the box only ever
   * searched seven hardcoded screens — so typing a real user's address returned nothing and the
   * feature looked broken. Both list endpoints accept a `search` parameter, so the promise is now
   * kept rather than reworded.
   */
  readonly userResults = signal<{ id: string; email: string; roles: string[] }[]>([]);
  readonly clientResults = signal<{ id: string; clientId: string; clientName: string }[]>([]);
  readonly searching = signal(false);

  readonly hasLiveResults = computed(
    () => this.userResults().length > 0 || this.clientResults().length > 0,
  );

  readonly noResultsAtAll = computed(
    () => this.searchQuery().trim().length > 0
      && this.filteredSearchItems().length === 0
      && !this.hasLiveResults()
      && !this.searching(),
  );

  private readonly liveQuery = new Subject<string>();
  private readonly userService = inject(UserService);
  private readonly clientService = inject(OAuthClientService);

  constructor() {
    // Debounced so a query is not issued per keystroke. switchMap drops the response to a query the
    // user has already typed past, which otherwise arrives late and overwrites newer results.
    this.liveQuery
      .pipe(
        debounceTime(250),
        switchMap((q) => {
          if (q.length < 2) {
            this.userResults.set([]);
            this.clientResults.set([]);
            this.searching.set(false);
            return of(null);
          }
          this.searching.set(true);
          return this.userService.list(q, null, '', 0, 5).pipe(catchError(() => of(null)));
        }),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.userResults.set(page ? page.content.map((u) => ({ id: u.id, email: u.email, roles: u.roles })) : []);
        this.searching.set(false);
      });

    this.liveQuery
      .pipe(
        debounceTime(250),
        switchMap((q) =>
          q.length < 2
            ? of(null)
            : this.clientService.list(q, null, 0, 5).pipe(catchError(() => of(null))),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.clientResults.set(
          page ? page.content.map((c) => ({ id: c.id, clientId: c.clientId, clientName: c.clientName })) : [],
        );
      });
  }

  onSearchQueryChange(value: string): void {
    this.searchQuery.set(value);
    this.liveQuery.next(value.trim().toLowerCase());
  }

  goToUser(): void {
    this.closeDropdown();
    const q = this.searchQuery().trim();
    this.searchQuery.set('');
    this.router.navigate(['/users'], { queryParams: { search: q } });
  }

  goToClient(): void {
    this.closeDropdown();
    const q = this.searchQuery().trim();
    this.searchQuery.set('');
    this.router.navigate(['/clients'], { queryParams: { search: q } });
  }

  @HostListener('document:keydown', ['$event'])
  handleKeyboard(event: KeyboardEvent): void {
    if ((event.metaKey || event.ctrlKey) && event.key === 'k') {
      event.preventDefault();
      this.searchInput()?.nativeElement.focus();
      this.isDropdownOpen.set(true);
    } else if (event.key === 'Escape' && this.isDropdownOpen()) {
      this.closeDropdown();
      this.searchInput()?.nativeElement.blur();
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.closeDropdown();
    }
  }

  onFocus(): void {
    this.isDropdownOpen.set(true);
  }

  closeDropdown(): void {
    this.isDropdownOpen.set(false);
  }

  clearSearch(): void {
    this.searchQuery.set('');
    this.liveQuery.next('');
    this.searchInput()?.nativeElement.focus();
  }

  selectSearchItem(item: SearchItem): void {
    this.closeDropdown();
    this.searchQuery.set('');
    this.liveQuery.next('');
    if (item.action) {
      item.action();
    } else if (item.route) {
      this.router.navigateByUrl(item.route);
    }
  }
}
