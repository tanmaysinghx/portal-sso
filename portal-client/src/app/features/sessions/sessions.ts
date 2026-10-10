import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { SessionService } from './services/session.service';
import { UserSession } from './models/session.model';
import { SnackbarService } from '../../core/services/snackbar.service';

@Component({
  selector: 'app-sessions',
  standalone: true,
  imports: [CommonModule, DatePipe],
  templateUrl: './sessions.html',
})
export class Sessions implements OnInit {
  private readonly sessionService = inject(SessionService);
  private readonly snackbar = inject(SnackbarService);

  readonly sessions = signal<UserSession[]>([]);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.loadSessions();
  }

  loadSessions(): void {
    this.loading.set(true);
    this.sessionService.getMySessions().subscribe({
      next: (sessions) => {
        this.sessions.set(sessions);
        this.loading.set(false);
      },
      error: () => {
        this.snackbar.error('Failed to load active sessions');
        this.loading.set(false);
      },
    });
  }

  revokeSession(sessionId: string, isCurrent: boolean): void {
    if (isCurrent) {
      if (!confirm('This is your current session. Revoking it will log you out immediately. Continue?')) {
        return;
      }
    }

    this.sessionService.revokeMySession(sessionId).subscribe({
      next: () => {
        this.snackbar.success('Session revoked successfully');
        if (isCurrent) {
          // If they killed their own session, force a reload to trigger logout flow
          window.location.href = '/sign-in';
        } else {
          this.loadSessions();
        }
      },
      error: () => {
        this.snackbar.error('Failed to revoke session');
      },
    });
  }
}
