import { Component, ElementRef, HostListener, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
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
      description: 'Create a new public PKCE client application',
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
      id: 'nav-product',
      title: 'Product Showcase',
      category: 'Navigation',
      description: 'View architecture specs, mockups, and client guide',
      route: '/product',
      icon: 'product',
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
    this.searchInput()?.nativeElement.focus();
  }

  selectSearchItem(item: SearchItem): void {
    this.closeDropdown();
    this.searchQuery.set('');
    if (item.action) {
      item.action();
    } else if (item.route) {
      this.router.navigateByUrl(item.route);
    }
  }
}
