import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AccessRequest, AccessRequestStatus, AdminSummary, SubmitAccessRequest } from '../models/access-request.model';

@Injectable({ providedIn: 'root' })
export class AccessRequestService {
  private readonly http = inject(HttpClient);

  // User endpoints
  submitRequest(request: SubmitAccessRequest): Observable<AccessRequest> {
    return this.http.post<AccessRequest>('/api/user/access-requests', request);
  }

  getMyRequests(): Observable<AccessRequest[]> {
    return this.http.get<AccessRequest[]>('/api/user/access-requests');
  }

  getAdmins(): Observable<AdminSummary[]> {
    return this.http.get<AdminSummary[]>('/api/user/access-requests/admins');
  }

  // Admin endpoints
  getAll(status?: AccessRequestStatus): Observable<AccessRequest[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<AccessRequest[]>('/api/admin/access-requests', { params });
  }

  getPendingCount(): Observable<{ pending: number }> {
    return this.http.get<{ pending: number }>('/api/admin/access-requests/count');
  }

  approve(id: string, comment?: string): Observable<AccessRequest> {
    return this.http.post<AccessRequest>(`/api/admin/access-requests/${id}/approve`, { comment });
  }

  deny(id: string, comment?: string): Observable<AccessRequest> {
    return this.http.post<AccessRequest>(`/api/admin/access-requests/${id}/deny`, { comment });
  }
}
