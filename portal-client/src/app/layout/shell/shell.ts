import { Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { Header } from '../header/header';
import { Sidebar } from '../sidebar/sidebar';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, Sidebar, Header],
  templateUrl: './shell.html',
})
export class Shell {
  private readonly router = inject(Router);

  /**
   * Drawer state, only meaningful below the `lg` breakpoint — above it the sidebar is permanently
   * visible and this is ignored. Owned here rather than in the sidebar because the header's
   * hamburger and the backdrop both need to change it.
   */
  readonly sidebarOpen = signal(false);

  constructor() {
    // Close on navigation. Without this, tapping a link on a phone leaves the drawer covering the
    // page you just asked for.
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.sidebarOpen.set(false));
  }

  toggleSidebar(): void {
    this.sidebarOpen.update((open) => !open);
  }

  closeSidebar(): void {
    this.sidebarOpen.set(false);
  }
}
