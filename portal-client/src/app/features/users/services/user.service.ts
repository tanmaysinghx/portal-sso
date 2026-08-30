import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../../core/models/page.model';
import { CreateUserRequest, PortalUser } from '../models/portal-user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/admin/users';

  list(
    search = '',
    enabled: boolean | null = null,
    role = '',
    page = 0,
    size = 25,
  ): Observable<Page<PortalUser>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (search.trim()) {
      params = params.set('search', search.trim());
    }
    if (enabled !== null) {
      params = params.set('enabled', enabled);
    }
    if (role) {
      params = params.set('role', role);
    }
    return this.http.get<Page<PortalUser>>(this.baseUrl, { params });
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
