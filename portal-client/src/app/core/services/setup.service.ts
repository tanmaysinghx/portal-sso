import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface SetupStatusResponse {
  setupRequired: boolean;
  databaseType: string;
  isEmbeddedDatabase: boolean;
  databaseProduct: string;
  version: string;
}

export interface SetupInitializeRequest {
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
}

export interface SetupInitializeResponse {
  success: boolean;
  message: string;
  email?: string;
}

@Injectable({ providedIn: 'root' })
export class SetupService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/public/setup';

  getStatus(): Observable<SetupStatusResponse> {
    return this.http.get<SetupStatusResponse>(`${this.baseUrl}/status`);
  }

  initialize(request: SetupInitializeRequest): Observable<SetupInitializeResponse> {
    return this.http.post<SetupInitializeResponse>(`${this.baseUrl}/initialize`, request);
  }
}
