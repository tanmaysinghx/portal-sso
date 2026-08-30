import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AuditActionOption, AuditEventPage, AuditFilters } from '../models/audit-event.model';

@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/admin/audit';

  list(filters: AuditFilters, page: number, size: number): Observable<AuditEventPage> {
    return this.http.get<AuditEventPage>(this.baseUrl, {
      params: this.toParams(filters).set('page', page).set('size', size),
    });
  }

  /** Read from the server rather than hardcoded, so a new action appears here without a redeploy. */
  actions(): Observable<AuditActionOption[]> {
    return this.http.get<AuditActionOption[]>(`${this.baseUrl}/actions`);
  }

  exportCsv(filters: AuditFilters): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/export`, {
      params: this.toParams(filters),
      responseType: 'blob',
    });
  }

  /**
   * Blank filters are omitted rather than sent as empty strings. The server rejects an action it
   * does not recognise, and "" is not a valid action.
   */
  private toParams(filters: AuditFilters): HttpParams {
    let params = new HttpParams();
    if (filters.action) {
      params = params.set('action', filters.action);
    }
    if (filters.actor.trim()) {
      params = params.set('actor', filters.actor.trim());
    }
    return params;
  }
}
