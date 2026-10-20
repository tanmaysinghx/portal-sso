import { Component, computed, effect, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { AccountService, PasswordExpiry, passwordExpiry } from '../../core/services/account.service';
import { AuthService } from '../../core/services/auth.service';
import { DatabaseMigrationService } from '../../core/services/database-migration.service';
import { Header } from '../header/header';
import { Sidebar } from '../sidebar/sidebar';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, Sidebar, Header],
  templateUrl: './shell.html',
})
export class Shell {
  private readonly router = inject(Router);
  readonly authService = inject(AuthService);
  private readonly dbMigrationService = inject(DatabaseMigrationService);

  readonly sidebarOpen = signal(false);
  readonly isEmbeddedDb = signal(false);
  readonly showDbBanner = signal(true);

  private readonly accountService = inject(AccountService);
  private readonly passwordStatus = signal<PasswordExpiry | null>(null);
  private readonly passwordBannerDismissed = signal(readDismissed());
  private readonly currentUrl = signal(this.router.url);
  /** Shown on every page inside the warning window, not just My Account, which nobody visits daily. */
  readonly passwordBanner = computed(() => {
    const status = this.passwordStatus();
    if (!status || this.passwordBannerDismissed() || this.currentUrl().startsWith('/account')) return null;
    return status.state === 'warning' || status.state === 'expired' ? status : null;
  });

  constructor() {
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => {
        this.sidebarOpen.set(false);
        this.currentUrl.set(this.router.url);
      });

    effect(() => {
      if (this.authService.isAdmin()) {
        this.dbMigrationService.getStatus().subscribe({
          next: (status) => this.isEmbeddedDb.set(status.isEmbedded),
          error: () => this.isEmbeddedDb.set(false),
        });
      }
    });

    effect(() => {
      if (this.authService.currentUser()) {
        this.accountService.get().subscribe({
          next: (account) => this.passwordStatus.set(passwordExpiry(account)),
          error: () => this.passwordStatus.set(null),
        });
      }
    });
  }

  toggleSidebar(): void {
    this.sidebarOpen.update((open) => !open);
  }

  closeSidebar(): void {
    this.sidebarOpen.set(false);
  }

  dismissDbBanner(): void {
    this.showDbBanner.set(false);
  }

  /** For this browser session only: the reminder should come back tomorrow, not stay gone. */
  dismissPasswordBanner(): void {
    this.passwordBannerDismissed.set(true);
    try {
      sessionStorage.setItem(PASSWORD_BANNER_KEY, '1');
    } catch {
      // Storage blocked: the dismissal still holds until the page reloads.
    }
  }
}

const PASSWORD_BANNER_KEY = 'portal-sso.password-expiry-banner-dismissed';

function readDismissed(): boolean {
  try {
    return sessionStorage.getItem(PASSWORD_BANNER_KEY) === '1';
  } catch {
    return false;
  }
}
