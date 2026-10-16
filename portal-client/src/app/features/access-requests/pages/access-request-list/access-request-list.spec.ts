import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { AccessRequestList } from './access-request-list';
import { AccessRequestService } from '../../services/access-request.service';
import { SnackbarService } from '../../../../core/services/snackbar.service';
import { AccessRequest } from '../../models/access-request.model';

describe('AccessRequestList Component', () => {
  let fixture: ComponentFixture<AccessRequestList>;
  let component: AccessRequestList;
  let serviceMock: {
    getAll: ReturnType<typeof vi.fn>;
    approve: ReturnType<typeof vi.fn>;
    deny: ReturnType<typeof vi.fn>;
  };
  let snackbarMock: {
    success: ReturnType<typeof vi.fn>;
    error: ReturnType<typeof vi.fn>;
  };

  const mockRequests: AccessRequest[] = [
    {
      id: 'req-1',
      user: { id: 'u-1', email: 'alice@example.com', firstName: 'Alice', lastName: 'Smith' },
      application: { id: 'app-1', name: 'Grafana', category: 'Monitoring', iconUrl: null },
      reviewer: { id: 'admin-1', email: 'admin@example.com', firstName: 'Admin', lastName: 'User' },
      status: 'PENDING',
      reason: 'Need access for on-call dashboards',
      reviewerComment: null,
      resolvedAt: null,
      createdAt: '2026-10-15T10:00:00Z',
    },
    {
      id: 'req-2',
      user: { id: 'u-2', email: 'bob@example.com', firstName: 'Bob', lastName: 'Jones' },
      application: { id: 'app-2', name: 'GitHub Enterprise', category: 'DevOps', iconUrl: 'https://example.com/icon.png' },
      reviewer: { id: 'admin-1', email: 'admin@example.com', firstName: 'Admin', lastName: 'User' },
      status: 'APPROVED',
      reason: 'Engineering team onboarding',
      reviewerComment: 'Approved as requested',
      resolvedAt: '2026-10-15T12:00:00Z',
      createdAt: '2026-10-14T09:00:00Z',
    },
    {
      id: 'req-3',
      user: { id: 'u-3', email: 'charlie@example.com', firstName: 'Charlie', lastName: 'Brown' },
      application: { id: 'app-3', name: 'Production DB', category: 'Infrastructure', iconUrl: null },
      reviewer: { id: 'admin-1', email: 'admin@example.com', firstName: 'Admin', lastName: 'User' },
      status: 'DENIED',
      reason: 'Testing database connection',
      reviewerComment: 'Direct prod DB access requires VP approval',
      resolvedAt: '2026-10-15T14:00:00Z',
      createdAt: '2026-10-15T13:00:00Z',
    },
  ];

  beforeEach(async () => {
    serviceMock = {
      getAll: vi.fn().mockReturnValue(of(mockRequests)),
      approve: vi.fn().mockReturnValue(of(undefined)),
      deny: vi.fn().mockReturnValue(of(undefined)),
    };
    snackbarMock = {
      success: vi.fn(),
      error: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [AccessRequestList],
      providers: [
        { provide: AccessRequestService, useValue: serviceMock },
        { provide: SnackbarService, useValue: snackbarMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AccessRequestList);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads requests and computes stats properly', () => {
    expect(component).toBeTruthy();
    expect(component.requests().length).toBe(3);
    expect(component.stats()).toEqual({
      total: 3,
      pending: 1,
      approved: 1,
      denied: 1,
    });
  });

  it('filters requests by status', () => {
    component.statusFilter.set('PENDING');
    expect(component.filteredRequests().length).toBe(1);
    expect(component.filteredRequests()[0].id).toBe('req-1');

    component.statusFilter.set('APPROVED');
    expect(component.filteredRequests().length).toBe(1);
    expect(component.filteredRequests()[0].id).toBe('req-2');

    component.statusFilter.set('ALL');
    expect(component.filteredRequests().length).toBe(3);
  });

  it('filters requests by search query', () => {
    component.searchQuery.set('Alice');
    expect(component.filteredRequests().length).toBe(1);
    expect(component.filteredRequests()[0].id).toBe('req-1');

    component.searchQuery.set('GitHub');
    expect(component.filteredRequests().length).toBe(1);
    expect(component.filteredRequests()[0].id).toBe('req-2');

    component.clearFilters();
    expect(component.searchQuery()).toBe('');
    expect(component.statusFilter()).toBe('ALL');
    expect(component.filteredRequests().length).toBe(3);
  });

  it('opens and closes review modal', () => {
    const req = mockRequests[0];
    component.openReviewModal(req, 'approve');
    expect(component.reviewModal()).toEqual({ request: req, action: 'approve' });

    component.closeReviewModal();
    expect(component.reviewModal()).toBeNull();
  });

  it('submits approval review', () => {
    const req = mockRequests[0];
    component.openReviewModal(req, 'approve');
    component.reviewComment.set('Looks good');
    component.submitReview();

    expect(serviceMock.approve).toHaveBeenCalledWith('req-1', 'Looks good');
    expect(snackbarMock.success).toHaveBeenCalledWith('Request approved successfully');
    expect(component.reviewModal()).toBeNull();
  });

  it('opens and closes details modal', () => {
    const req = mockRequests[1];
    component.openDetailsModal(req);
    expect(component.detailsModal()).toBe(req);

    component.closeDetailsModal();
    expect(component.detailsModal()).toBeNull();
  });
});
