import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateUserRequest, PortalUser } from '../models/portal-user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/admin/users';

  list(): Observable<PortalUser[]> {
    return this.http.get<PortalUser[]>(this.baseUrl);
  }

  create(request: CreateUserRequest): Observable<PortalUser> {
    return this.http.post<PortalUser>(this.baseUrl, request);
  }

  setEnabled(id: string, enabled: boolean): Observable<PortalUser> {
    return this.http.patch<PortalUser>(`${this.baseUrl}/${id}`, { enabled });
  }

  /** Clears a lockout applied after too many failed sign-ins, and resets the attempt counter. */
  unlock(id: string): Observable<PortalUser> {
    return this.http.post<PortalUser>(`${this.baseUrl}/${id}/unlock`, null);
  }

  /**
   * Replaces the user's roles with this complete set, not a delta. The server refuses a change that
   * would remove your own admin role or the last remaining administrator.
   */
  setRoles(id: string, roles: string[]): Observable<PortalUser> {
    return this.http.put<PortalUser>(`${this.baseUrl}/${id}/roles`, { roles });
  }

  /** Disables/resets MFA for a user who lost their device. */
  resetMfa(id: string): Observable<PortalUser> {
    return this.http.post<PortalUser>(`${this.baseUrl}/${id}/mfa/reset`, null);
  }
}
