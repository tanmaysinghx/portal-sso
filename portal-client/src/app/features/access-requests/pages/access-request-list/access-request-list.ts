import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AccessRequest, AccessRequestStatus } from '../../models/access-request.model';
import { AccessRequestService } from '../../services/access-request.service';
import { SnackbarService } from '../../../../core/services/snackbar.service';

@Component({
  selector: 'app-access-request-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './access-request-list.html',
})
export class AccessRequestList implements OnInit {
  private readonly accessRequestService = inject(AccessRequestService);
  private readonly snackbar = inject(SnackbarService);

  requests = signal<AccessRequest[]>([]);
  loading = signal(true);
  statusFilter = signal<AccessRequestStatus | 'ALL'>('PENDING');

  filteredRequests = computed(() => {
    const status = this.statusFilter();
    if (status === 'ALL') {
      return this.requests();
    }
    return this.requests().filter(r => r.status === status);
  });

  stats = computed(() => {
    const all = this.requests();
    return {
      pending: all.filter(r => r.status === 'PENDING').length,
      approved: all.filter(r => r.status === 'APPROVED').length,
      denied: all.filter(r => r.status === 'DENIED').length,
    };
  });

  reviewModal = signal<{ request: AccessRequest; action: 'approve' | 'deny' } | null>(null);
  reviewComment = signal('');
  submitting = signal(false);

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
      }
    });
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
    const obs$ = modal.action === 'approve'
      ? this.accessRequestService.approve(modal.request.id, this.reviewComment() || undefined)
      : this.accessRequestService.deny(modal.request.id, this.reviewComment() || undefined);

    obs$.subscribe({
      next: () => {
        this.snackbar.success(`Request ${modal.action === 'approve' ? 'approved' : 'denied'} successfully`);
        this.closeReviewModal();
        this.loadRequests();
        this.submitting.set(false);
      },
      error: () => {
        this.snackbar.error(`Failed to ${modal.action} request`);
        this.submitting.set(false);
      }
    });
  }

  getInitials(firstName: string | null, lastName: string | null): string {
    const first = firstName ? firstName.charAt(0) : '';
    const last = lastName ? lastName.charAt(0) : '';
    const initials = (first + last).toUpperCase();
    return initials || '?';
  }

  formatDate(isoString: string): string {
    if (!isoString) return '—';
    try {
      return new Intl.DateTimeFormat('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: 'numeric',
        minute: '2-digit'
      }).format(new Date(isoString));
    } catch {
      return isoString;
    }
  }
}
