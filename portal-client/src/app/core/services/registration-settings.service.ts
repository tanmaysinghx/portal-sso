import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface PublicRegistrationSettings {
  enabled: boolean;
  requiresApproval: boolean;
}

@Injectable({ providedIn: 'root' })
export class RegistrationSettingsAdminService {
  private readonly http = inject(HttpClient);

  get(): Observable<PublicRegistrationSettings> {
    return this.http.get<PublicRegistrationSettings>('/api/admin/registration');
  }

  save(settings: PublicRegistrationSettings): Observable<PublicRegistrationSettings> {
    return this.http.put<PublicRegistrationSettings>('/api/admin/registration', settings);
  }
}
