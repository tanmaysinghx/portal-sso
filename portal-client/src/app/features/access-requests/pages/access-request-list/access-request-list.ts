import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AccessRequest, AccessRequestStatus } from '../../models/access-request.model';
import { AccessRequestService } from '../../services/access-request.service';
import { SnackbarService } from '../../../../core/services/snackbar.service';
import { Badge, BadgeTone } from '../../../../shared/components/badge/badge';

@Component({
  selector: 'app-access-request-list',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, Badge],
  templateUrl: './access-request-list.html',
})
export class AccessRequestList implements OnInit {
  private readonly accessRequestService = inject(AccessRequestService);
  private readonly snackbar = inject(SnackbarService);

  readonly requests = signal<AccessRequest[]>([]);
  readonly loading = signal(true);
  readonly searchQuery = signal('');
  readonly statusFilter = signal<AccessRequestStatus | 'ALL'>('ALL');

  readonly stats = computed(() => {
    const all = this.requests();
    return {
      total: all.length,
      pending: all.filter((r) => r.status === 'PENDING').length,
      approved: all.filter((r) => r.status === 'APPROVED').length,
      denied: all.filter((r) => r.status === 'DENIED').length,
    };
  });

  readonly filteredRequests = computed(() => {
    const q = this.searchQuery().trim().toLowerCase();
    const status = this.statusFilter();

    return this.requests().filter((req) => {
      // Filter by status
      if (status !== 'ALL' && req.status !== status) {
        return false;
      }

      // Filter by search query
      if (q) {
        const userName = `${req.user.firstName || ''} ${req.user.lastName || ''}`.toLowerCase();
        const userEmail = req.user.email.toLowerCase();
        const appName = req.application.name.toLowerCase();
        const appCategory = (req.application.category || '').toLowerCase();
        const reason = (req.reason || '').toLowerCase();

        return (
          userName.includes(q) ||
          userEmail.includes(q) ||
          appName.includes(q) ||
          appCategory.includes(q) ||
          reason.includes(q)
        );
      }

      return true;
    });
  });

  readonly hasActiveFilters = computed(
    () => this.searchQuery().trim() !== '' || this.statusFilter() !== 'ALL',
  );

  readonly rangeLabel = computed(() => {
    const count = this.filteredRequests().length;
    const total = this.requests().length;
    if (count === total) {
      return `${total} request${total === 1 ? '' : 's'}`;
    }
    return `Showing ${count} of ${total} requests`;
  });

  readonly reviewModal = signal<{ request: AccessRequest; action: 'approve' | 'deny' } | null>(null);
  readonly reviewComment = signal('');
  readonly submitting = signal(false);

  readonly detailsModal = signal<AccessRequest | null>(null);

  ngOnInit(): void {
    this.loadRequests();
  }

  loadRequests(): void {
    this.loading.set(true);
    this.accessRequestService.getAll().subscribe({
      next: (data) => {
        this.requests.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.snackbar.error('Failed to load access requests');
        this.loading.set(false);
      },
    });
  }

  clearFilters(): void {
    this.searchQuery.set('');
    this.statusFilter.set('ALL');
  }

  openReviewModal(request: AccessRequest, action: 'approve' | 'deny'): void {
    this.reviewModal.set({ request, action });
    this.reviewComment.set('');
  }

  closeReviewModal(): void {
    this.reviewModal.set(null);
    this.reviewComment.set('');
  }

  submitReview(): void {
    const modal = this.reviewModal();
    if (!modal) return;

    this.submitting.set(true);
    const obs$ =
      modal.action === 'approve'
        ? this.accessRequestService.approve(modal.request.id, this.reviewComment() || undefined)
        : this.accessRequestService.deny(modal.request.id, this.reviewComment() || undefined);

    obs$.subscribe({
      next: () => {
        this.snackbar.success(
          `Request ${modal.action === 'approve' ? 'approved' : 'denied'} successfully`,
        );
        this.closeReviewModal();
        if (this.detailsModal()?.id === modal.request.id) {
          this.closeDetailsModal();
        }
        this.loadRequests();
        this.submitting.set(false);
      },
      error: () => {
        this.snackbar.error(`Failed to ${modal.action} request`);
        this.submitting.set(false);
      },
    });
  }

  openDetailsModal(request: AccessRequest): void {
    this.detailsModal.set(request);
  }

  closeDetailsModal(): void {
    this.detailsModal.set(null);
  }

  getStatusTone(status: AccessRequestStatus): BadgeTone {
    switch (status) {
      case 'PENDING':
        return 'brand';
      case 'APPROVED':
        return 'success';
      case 'DENIED':
        return 'danger';
      default:
        return 'neutral';
    }
  }

  getInitials(firstName: string | null, lastName: string | null): string {
    const first = firstName ? firstName.charAt(0) : '';
    const last = lastName ? lastName.charAt(0) : '';
    const initials = (first + last).toUpperCase();
    return initials || '?';
  }
}
