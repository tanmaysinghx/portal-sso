import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';
import { BrandingService } from '../../../../core/services/branding.service';
import { UserApplication } from '../../../applications/models/application.model';
import { ApplicationService } from '../../../applications/services/application.service';
import { AccessRequestService } from '../../../access-requests/services/access-request.service';
import { AdminSummary } from '../../../access-requests/models/access-request.model';
import { SnackbarService } from '../../../../core/services/snackbar.service';

@Component({
  selector: 'app-user-apps',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './user-apps.html',
})
export class UserApps {
  private readonly applicationService = inject(ApplicationService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  readonly brandingService = inject(BrandingService);
  private readonly accessRequestService = inject(AccessRequestService);
  private readonly snackbar = inject(SnackbarService);

  readonly currentUser = this.authService.currentUser;
  readonly isAdmin = this.authService.isAdmin;

  readonly applications = signal<UserApplication[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly searchQuery = signal('');
  readonly selectedCategory = signal('all');

  readonly showRequestModal = signal<UserApplication | null>(null);
  readonly admins = signal<AdminSummary[]>([]);
  readonly selectedAdminId = signal<string>('');
  readonly requestReason = signal('');
  readonly submittingRequest = signal(false);

  readonly userInitial = computed(() => {
    const email = this.currentUser()?.email ?? '';
    return email ? email.charAt(0).toUpperCase() : '?';
  });

  readonly categories = computed(() => {
    const cats = new Set<string>();
    for (const app of this.applications()) {
      if (app.category) cats.add(app.category);
    }
    return ['all', ...Array.from(cats).sort()];
  });

  readonly categoryCounts = computed(() => {
    const counts = new Map<string, number>();
    counts.set('all', this.applications().length);
    for (const app of this.applications()) {
      const cat = app.category || 'General';
      counts.set(cat, (counts.get(cat) ?? 0) + 1);
    }
    return counts;
  });

  readonly filteredApplications = computed(() => {
    const q = this.searchQuery().toLowerCase().trim();
    const cat = this.selectedCategory();

    return this.applications().filter((app) => {
      const matchesSearch =
        !q ||
        app.name.toLowerCase().includes(q) ||
        (app.description && app.description.toLowerCase().includes(q));

      const matchesCat = cat === 'all' || app.category.toLowerCase() === cat.toLowerCase();

      return matchesSearch && matchesCat;
    });
  });

  readonly accessibleApps = computed(() => this.filteredApplications().filter(a => a.accessible));
  readonly requestableApps = computed(() => this.filteredApplications().filter(a => !a.accessible));

  constructor() {
    this.loadApplications();
  }

  loadApplications(): void {
    this.loading.set(true);
    this.error.set(null);
    this.applicationService.getUserApplications().subscribe({
      next: (apps) => {
        this.applications.set(apps);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Unable to load your applications. Please try again later.');
        this.loading.set(false);
      },
    });
  }

  getInitials(name: string): string {
    return name
      .split(' ')
      .slice(0, 2)
      .map((w) => w.charAt(0).toUpperCase())
      .join('');
  }

  getGradient(name: string): string {
    const gradients = [
      'from-amber-500 to-red-600',
      'from-blue-600 to-indigo-700',
      'from-emerald-500 to-teal-700',
      'from-purple-600 to-pink-600',
      'from-cyan-500 to-blue-600',
      'from-orange-500 to-amber-600',
    ];
    let hash = 0;
    for (let i = 0; i < name.length; i++) {
      hash = name.charCodeAt(i) + ((hash << 5) - hash);
    }
    const index = Math.abs(hash) % gradients.length;
    return gradients[index];
  }

  logout(): void {
    this.authService.logout().subscribe(() => {
      this.router.navigateByUrl('/sign-in');
    });
  }

  openRequestModal(app: UserApplication): void {
    this.showRequestModal.set(app);
    if (this.admins().length === 0) {
      this.accessRequestService.getAdmins().subscribe({
        next: (admins) => {
          this.admins.set(admins);
          if (admins.length > 0) {
            this.selectedAdminId.set(admins[0].id);
          }
        },
        error: () => this.snackbar.error('Failed to load administrators')
      });
    } else {
      if (this.admins().length > 0) {
        this.selectedAdminId.set(this.admins()[0].id);
      }
    }
  }

  closeRequestModal(): void {
    this.showRequestModal.set(null);
    this.requestReason.set('');
    this.selectedAdminId.set(this.admins().length > 0 ? this.admins()[0].id : '');
  }

  submitAccessRequest(): void {
    const app = this.showRequestModal();
    if (!app || !this.selectedAdminId()) return;

    this.submittingRequest.set(true);
    this.accessRequestService.submitRequest({
      applicationId: app.id,
      reviewerId: this.selectedAdminId(),
      reason: this.requestReason() || undefined
    }).subscribe({
      next: () => {
        this.applications.update(apps => 
          apps.map(a => a.id === app.id ? { ...a, pendingRequestStatus: 'PENDING' } : a)
        );
        this.snackbar.success(`Access request sent for ${app.name}`);
        this.closeRequestModal();
        this.submittingRequest.set(false);
      },
      error: () => {
        this.snackbar.error(`Failed to request access for ${app.name}`);
        this.submittingRequest.set(false);
      }
    });
  }
}
