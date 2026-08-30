import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DashboardStats, StatsRange } from '../models/dashboard-stats.model';

@Injectable({ providedIn: 'root' })
export class DashboardStatsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/admin/stats';

  load(range: StatsRange): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(this.baseUrl, { params: { range } });
  }

  /** Server-rendered CSV, fetched as a blob so the Content-Disposition filename is preserved. */
  exportCsv(range: StatsRange): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/export`, {
      params: { range },
      responseType: 'blob',
    });
  }
}
