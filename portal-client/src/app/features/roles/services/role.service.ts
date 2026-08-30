import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateRoleRequest, PortalRole } from '../models/role.model';

@Injectable({ providedIn: 'root' })
export class RoleService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/admin/roles';

  list(): Observable<PortalRole[]> {
    return this.http.get<PortalRole[]>(this.baseUrl);
  }

  create(request: CreateRoleRequest): Observable<PortalRole> {
    return this.http.post<PortalRole>(this.baseUrl, request);
  }

  /** Only the description is editable — a role's name is its granted authority, so it is fixed. */
  updateDescription(id: string, description: string): Observable<PortalRole> {
    return this.http.put<PortalRole>(`${this.baseUrl}/${id}`, { description });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
