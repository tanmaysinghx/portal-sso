import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type SmtpTransportSecurity = 'NONE' | 'STARTTLS' | 'SSL_TLS';

/**
 * The stored configuration. There is no password field by design — the server never sends the
 * secret back, and `passwordSet` is all the form needs to show that one exists.
 */
export interface SmtpSettingsResponse {
  enabled: boolean;
  /** Whether a send would be attempted now: enabled, with host, port and from address present. */
  usable: boolean;
  host: string | null;
  port: number | null;
  transportSecurity: SmtpTransportSecurity;
  username: string | null;
  passwordSet: boolean;
  fromAddress: string | null;
  fromName: string | null;
  replyToAddress: string | null;
  updatedBy: string | null;
  updatedAt: string | null;
  lastTestAt: string | null;
  lastTestSuccessful: boolean | null;
  lastTestError: string | null;
}

/**
 * `password` is three-valued on purpose: omit it (undefined) to keep the stored secret, send `''`
 * to clear it, send a value to replace it. The form sends undefined unless the administrator
 * actually typed in the password box.
 */
export interface UpdateSmtpSettingsRequest {
  enabled: boolean;
  host?: string | null;
  port?: number | null;
  transportSecurity: SmtpTransportSecurity;
  username?: string | null;
  password?: string;
  fromAddress?: string | null;
  fromName?: string | null;
  replyToAddress?: string | null;
}

export interface SendTestEmailRequest {
  recipient: string;
  /** Unsaved settings to test. Omit to test the stored configuration. */
  settings?: UpdateSmtpSettingsRequest;
}

export interface SendTestEmailResponse {
  success: boolean;
  message: string;
  detail: string | null;
}

@Injectable({ providedIn: 'root' })
export class SmtpService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/admin/smtp';

  getSettings(): Observable<SmtpSettingsResponse> {
    return this.http.get<SmtpSettingsResponse>(this.baseUrl);
  }

  saveSettings(req: UpdateSmtpSettingsRequest): Observable<SmtpSettingsResponse> {
    return this.http.put<SmtpSettingsResponse>(this.baseUrl, req);
  }

  sendTestEmail(req: SendTestEmailRequest): Observable<SendTestEmailResponse> {
    return this.http.post<SendTestEmailResponse>(`${this.baseUrl}/test`, req);
  }
}
