import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface MfaStatusResponse {
  mfaEnabled: boolean;
}

export interface MfaSetupResponse {
  secret: string;
  provisioningUri: string;
}

export interface MfaConfirmResponse {
  mfaEnabled: boolean;
  recoveryCodes: string[];
}

@Injectable({ providedIn: 'root' })
export class MfaClientService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/mfa';

  getStatus(): Observable<MfaStatusResponse> {
    return this.http.get<MfaStatusResponse>(`${this.baseUrl}/status`);
  }

  setup(): Observable<MfaSetupResponse> {
    return this.http.post<MfaSetupResponse>(`${this.baseUrl}/setup`, {});
  }

  confirm(code: string): Observable<MfaConfirmResponse> {
    return this.http.post<MfaConfirmResponse>(`${this.baseUrl}/confirm`, { code });
  }

  disable(password: string): Observable<MfaStatusResponse> {
    return this.http.post<MfaStatusResponse>(`${this.baseUrl}/disable`, { password });
  }
}
