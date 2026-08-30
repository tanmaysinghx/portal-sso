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
}
