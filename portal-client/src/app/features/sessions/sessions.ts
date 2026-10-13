import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { SessionService } from './services/session.service';
import { EnrichedUserSession } from './models/session.model';
import { SnackbarService } from '../../core/services/snackbar.service';
import { getIpType, getRelativeTime, parseUserAgent } from './utils/user-agent-parser';

@Component({
  selector: 'app-sessions',
  standalone: true,
  imports: [CommonModule, DatePipe],
  templateUrl: './sessions.html',
})
export class Sessions implements OnInit {
  private readonly sessionService = inject(SessionService);
  private readonly snackbar = inject(SnackbarService);

  readonly sessions = signal<EnrichedUserSession[]>([]);
  readonly loading = signal(true);
  readonly revokingId = signal<string | null>(null);
  readonly revokingAllOthers = signal(false);

  // Modals state
  readonly sessionToRevoke = signal<EnrichedUserSession | null>(null);
  readonly confirmRevokeAllOpen = signal(false);

  // Expanded details state (set of expanded session IDs)
  readonly expandedSessions = signal<Set<string>>(new Set());

  // Copied feedback
  readonly copiedSessionId = signal<string | null>(null);

  readonly currentSession = computed(() => this.sessions().find((s) => s.isCurrent));
  readonly otherSessions = computed(() => this.sessions().filter((s) => !s.isCurrent));

  ngOnInit(): void {
    this.loadSessions();
  }

  loadSessions(): void {
    this.loading.set(true);
    this.sessionService.getMySessions().subscribe({
      next: (rawSessions) => {
        const enriched: EnrichedUserSession[] = rawSessions.map((session) => {
          const isCurrent = Boolean(session.currentSession ?? session.isCurrent);
          return {
            ...session,
            isCurrent,
            device: parseUserAgent(session.userAgent),
            relativeLastActive: getRelativeTime(session.lastAccessedTime, isCurrent),
            ipType: getIpType(session.ipAddress),
          };
        });

        // Ensure current session appears first, followed by others sorted by recency
        enriched.sort((a, b) => {
          if (a.isCurrent) return -1;
          if (b.isCurrent) return 1;
          return new Date(b.lastAccessedTime).getTime() - new Date(a.lastAccessedTime).getTime();
        });

        this.sessions.set(enriched);
        this.loading.set(false);
      },
      error: () => {
        this.snackbar.error('Failed to load active sessions');
        this.loading.set(false);
      },
    });
  }

  toggleDetails(sessionId: string): void {
    const current = new Set(this.expandedSessions());
    if (current.has(sessionId)) {
      current.delete(sessionId);
    } else {
      current.add(sessionId);
    }
    this.expandedSessions.set(current);
  }

  isExpanded(sessionId: string): boolean {
    return this.expandedSessions().has(sessionId);
  }

  copySessionId(sessionId: string): void {
    if (navigator?.clipboard) {
      navigator.clipboard.writeText(sessionId).then(() => {
        this.copiedSessionId.set(sessionId);
        this.snackbar.success('Session ID copied to clipboard');
        setTimeout(() => {
          if (this.copiedSessionId() === sessionId) {
            this.copiedSessionId.set(null);
          }
        }, 2000);
      });
    }
  }

  promptRevoke(session: EnrichedUserSession): void {
    this.sessionToRevoke.set(session);
  }

  closeRevokeModal(): void {
    this.sessionToRevoke.set(null);
  }

  confirmRevoke(): void {
    const session = this.sessionToRevoke();
    if (!session) return;

    this.revokingId.set(session.id);
    this.sessionService.revokeMySession(session.id).subscribe({
      next: () => {
        this.revokingId.set(null);
        this.closeRevokeModal();
        this.snackbar.success(session.isCurrent ? 'Signed out successfully' : 'Session revoked successfully');
        if (session.isCurrent) {
          window.location.href = '/sign-in';
        } else {
          this.sessions.update((list) => list.filter((s) => s.id !== session.id));
        }
      },
      error: () => {
        this.revokingId.set(null);
        this.snackbar.error('Failed to revoke session');
      },
    });
  }

  promptRevokeAllOthers(): void {
    this.confirmRevokeAllOpen.set(true);
  }

  closeRevokeAllModal(): void {
    this.confirmRevokeAllOpen.set(false);
  }

  confirmRevokeAllOthers(): void {
    this.revokingAllOthers.set(true);
    this.sessionService.revokeOtherSessions().subscribe({
      next: () => {
        this.revokingAllOthers.set(false);
        this.closeRevokeAllModal();
        this.snackbar.success('All other sessions revoked successfully');
        this.loadSessions();
      },
      error: () => {
        this.revokingAllOthers.set(false);
        this.snackbar.error('Failed to revoke other sessions');
      },
    });
  }
}
