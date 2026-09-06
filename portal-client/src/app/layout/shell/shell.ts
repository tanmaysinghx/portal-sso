import { Component, effect, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
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

  constructor() {
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.sidebarOpen.set(false));

    effect(() => {
      if (this.authService.isAdmin()) {
        this.dbMigrationService.getStatus().subscribe({
          next: (status) => this.isEmbeddedDb.set(status.isEmbedded),
          error: () => this.isEmbeddedDb.set(false),
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
}

