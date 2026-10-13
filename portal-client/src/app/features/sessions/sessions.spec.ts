import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { Sessions } from './sessions';
import { SessionService } from './services/session.service';
import { SnackbarService } from '../../core/services/snackbar.service';
import { UserSession } from './models/session.model';

describe('Sessions Component', () => {
  let fixture: ComponentFixture<Sessions>;
  let component: Sessions;
  let sessionServiceMock: {
    getMySessions: ReturnType<typeof vi.fn>;
    revokeMySession: ReturnType<typeof vi.fn>;
    revokeOtherSessions: ReturnType<typeof vi.fn>;
  };
  let snackbarMock: {
    success: ReturnType<typeof vi.fn>;
    error: ReturnType<typeof vi.fn>;
  };

  const mockSessions: UserSession[] = [
    {
      id: 'session-curr-123',
      creationTime: '2026-10-12T10:00:00Z',
      lastAccessedTime: '2026-10-13T12:00:00Z',
      ipAddress: '127.0.0.1',
      userAgent:
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
      currentSession: true,
    },
    {
      id: 'session-other-456',
      creationTime: '2026-10-10T08:00:00Z',
      lastAccessedTime: '2026-10-12T09:00:00Z',
      ipAddress: '198.51.100.22',
      userAgent:
        'Mozilla/5.0 (iPhone; CPU iPhone OS 17_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3.1 Mobile/15E148 Safari/604.1',
      currentSession: false,
    },
  ];

  beforeEach(async () => {
    sessionServiceMock = {
      getMySessions: vi.fn().mockReturnValue(of(mockSessions)),
      revokeMySession: vi.fn().mockReturnValue(of(undefined)),
      revokeOtherSessions: vi.fn().mockReturnValue(of(undefined)),
    };
    snackbarMock = {
      success: vi.fn(),
      error: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [Sessions],
      providers: [
        { provide: SessionService, useValue: sessionServiceMock },
        { provide: SnackbarService, useValue: snackbarMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Sessions);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the component and loads enriched sessions', () => {
    expect(component).toBeTruthy();
    expect(component.sessions().length).toBe(2);

    const curr = component.currentSession();
    expect(curr).toBeDefined();
    expect(curr?.id).toBe('session-curr-123');
    expect(curr?.isCurrent).toBe(true);
    expect(curr?.device.browser).toBe('Chrome');
    expect(curr?.device.os).toBe('Windows');
    expect(curr?.ipType).toBe('Localhost');

    const others = component.otherSessions();
    expect(others.length).toBe(1);
    expect(others[0].id).toBe('session-other-456');
    expect(others[0].device.deviceType).toBe('mobile');
    expect(others[0].device.displayName).toBe('Safari on iPhone');
  });

  it('opens and closes session details modal', () => {
    const target = component.sessions()[0];
    expect(component.detailsSession()).toBeNull();
    component.openDetailsModal(target);
    expect(component.detailsSession()).toBe(target);
    component.closeDetailsModal();
    expect(component.detailsSession()).toBeNull();
  });

  it('opens and closes single session revoke modal', () => {
    const target = component.otherSessions()[0];
    component.promptRevoke(target);
    expect(component.sessionToRevoke()).toBe(target);

    component.closeRevokeModal();
    expect(component.sessionToRevoke()).toBeNull();
  });

  it('revokes an other session and updates list', () => {
    const target = component.otherSessions()[0];
    component.promptRevoke(target);
    component.confirmRevoke();

    expect(sessionServiceMock.revokeMySession).toHaveBeenCalledWith('session-other-456');
    expect(snackbarMock.success).toHaveBeenCalledWith('Session revoked successfully');
    expect(component.sessions().length).toBe(1);
  });

  it('opens and closes revoke-all-others modal and executes revocation', () => {
    component.promptRevokeAllOthers();
    expect(component.confirmRevokeAllOpen()).toBe(true);

    component.confirmRevokeAllOthers();
    expect(sessionServiceMock.revokeOtherSessions).toHaveBeenCalled();
    expect(snackbarMock.success).toHaveBeenCalledWith('All other sessions revoked successfully');
  });
});
