import { Component, computed, inject, input, output } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { BrandingService } from '../../core/services/branding.service';
import { ReleaseVersionService } from '../../core/services/release-version.service';
import { AccessRequestService } from '../../features/access-requests/services/access-request.service';
import { signal, OnInit } from '@angular/core';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  host: {
    class: 'block',
  },
})
export class Sidebar implements OnInit {
  /** Whether the off-canvas drawer is showing. Ignored at lg and above, where it is always visible. */
  readonly open = input(false);

  /** Raised when the backdrop or the close button is used. */
  readonly dismiss = output<void>();

  readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  readonly brandingService = inject(BrandingService);
  private readonly accessRequestService = inject(AccessRequestService);
  readonly releaseVersionService = inject(ReleaseVersionService);

  readonly pendingRequestsCount = signal(0);

  ngOnInit() {
    this.releaseVersionService.loadLatest();
    // Admin-only endpoint: asking as an ordinary user just produces a 403 in the console.
    if (!this.authService.isAdmin()) {
      return;
    }
    this.accessRequestService.getPendingCount().subscribe(
      res => this.pendingRequestsCount.set(res.pending)
    );
  }

  readonly currentUser = this.authService.currentUser;
  readonly initial = computed(() => (this.currentUser()?.email ?? '?').charAt(0).toUpperCase());
  readonly version = this.releaseVersionService.version;

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigateByUrl('/sign-in'));
  }
}
