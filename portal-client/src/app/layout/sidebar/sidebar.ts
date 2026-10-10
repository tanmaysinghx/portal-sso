import { Component, computed, inject, input, output } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { BrandingService } from '../../core/services/branding.service';
import { environment } from '../../../environments/environment';
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

  readonly pendingRequestsCount = signal(0);

  ngOnInit() {
    this.accessRequestService.getPendingCount().subscribe(
      res => this.pendingRequestsCount.set(res.pending)
    );
  }

  readonly currentUser = this.authService.currentUser;
  readonly initial = computed(() => (this.currentUser()?.email ?? '?').charAt(0).toUpperCase());
  readonly version = environment.version;

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigateByUrl('/sign-in'));
  }
}
