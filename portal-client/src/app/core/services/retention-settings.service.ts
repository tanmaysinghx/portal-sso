import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface RetentionSettings {
  loginEventsHours: number;
  auditEventsHours: number;
}

/** Admin API for how long sign-in attempt and audit records are retained. */
@Injectable({ providedIn: 'root' })
export class RetentionSettingsService {
  private readonly http = inject(HttpClient);

  get(): Observable<RetentionSettings> {
    return this.http.get<RetentionSettings>('/api/admin/analytics/retention');
  }

  save(settings: RetentionSettings): Observable<RetentionSettings> {
    return this.http.put<RetentionSettings>('/api/admin/analytics/retention', settings);
  }
}
