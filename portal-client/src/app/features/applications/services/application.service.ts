import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Application,
  CreateApplicationRequest,
  UpdateApplicationRequest,
  UserApplication,
} from '../models/application.model';

@Injectable({ providedIn: 'root' })
export class ApplicationService {
  private readonly http = inject(HttpClient);

  getAdminApplications(filter?: {
    search?: string;
    category?: string;
    enabled?: boolean;
  }): Observable<Application[]> {
    let params = new HttpParams();
    if (filter?.search) {
      params = params.set('search', filter.search);
    }
    if (filter?.category && filter.category !== 'all') {
      params = params.set('category', filter.category);
    }
    if (filter?.enabled !== undefined) {
      params = params.set('enabled', String(filter.enabled));
    }
    return this.http.get<Application[]>('/api/admin/applications', { params });
  }

  getApplication(id: string): Observable<Application> {
    return this.http.get<Application>(`/api/admin/applications/${id}`);
  }

  createApplication(request: CreateApplicationRequest): Observable<Application> {
    return this.http.post<Application>('/api/admin/applications', request);
  }

  updateApplication(id: string, request: UpdateApplicationRequest): Observable<Application> {
    return this.http.put<Application>(`/api/admin/applications/${id}`, request);
  }

  deleteApplication(id: string): Observable<void> {
    return this.http.delete<void>(`/api/admin/applications/${id}`);
  }

  getUserApplications(): Observable<UserApplication[]> {
    return this.http.get<UserApplication[]>('/api/user/applications');
  }
}
