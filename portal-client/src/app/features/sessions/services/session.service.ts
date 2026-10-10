import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { UserSession } from '../models/session.model';

@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly http = inject(HttpClient);

  /** Get active sessions for the currently logged-in user */
  getMySessions(): Observable<UserSession[]> {
    return this.http.get<UserSession[]>('/api/user/sessions');
  }

  /** Revoke a specific session for the currently logged-in user */
  revokeMySession(sessionId: string): Observable<void> {
    return this.http.delete<void>(`/api/user/sessions/${sessionId}`);
  }

  /** [ADMIN ONLY] Get active sessions for a specific user */
  getUserSessions(userId: string): Observable<UserSession[]> {
    return this.http.get<UserSession[]>(`/api/admin/users/${userId}/sessions`);
  }

  /** [ADMIN ONLY] Revoke a specific session for a specific user */
  revokeUserSession(userId: string, sessionId: string): Observable<void> {
    return this.http.delete<void>(`/api/admin/users/${userId}/sessions/${sessionId}`);
  }
}
