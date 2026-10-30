import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PasswordPolicy } from './account.service';

export interface SetupStatusResponse {
  setupRequired: boolean;
  /** False while setup is required means the wizard cannot be used: auto-generation is off. */
  setupKeyIssued: boolean;
  /** Where the operator finds the key; present only while setup is required. */
  setupKeyFile?: string;
  /** The policy with the administrator's higher length floor already applied. */
  passwordPolicy: PasswordPolicy;
  databaseType: string;
  isEmbeddedDatabase: boolean;
  databaseProduct: string;
}

export interface SetupInitializeRequest {
  setupKey: string;
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
