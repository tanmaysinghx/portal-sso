import { Component, computed, inject, input, output } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { BrandingService } from '../../core/services/branding.service';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  host: {
    class: 'block',
  },
})
export class Sidebar {
  /** Whether the off-canvas drawer is showing. Ignored at lg and above, where it is always visible. */
  readonly open = input(false);

  /** Raised when the backdrop or the close button is used. */
  readonly dismiss = output<void>();

  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  readonly brandingService = inject(BrandingService);

  readonly currentUser = this.authService.currentUser;
  readonly initial = computed(() => (this.currentUser()?.email ?? '?').charAt(0).toUpperCase());

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigateByUrl('/sign-in'));
  }
}
