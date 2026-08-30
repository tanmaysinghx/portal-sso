import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PortalUser } from '../models/portal-user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/admin/users';

  list(): Observable<PortalUser[]> {
    return this.http.get<PortalUser[]>(this.baseUrl);
  }

  setEnabled(id: string, enabled: boolean): Observable<PortalUser> {
    return this.http.patch<PortalUser>(`${this.baseUrl}/${id}`, { enabled });
  }
}
