import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type EmailTemplateType =
  | 'WELCOME'
  | 'NEW_DEVICE_SIGN_IN'
  | 'SIGN_IN_NOTICE'
  | 'MAGIC_LINK_SIGN_IN'
  | 'MAGIC_LINK_SIGN_UP';

/** `NO_RELAY` is "switched on, but nothing can be delivered" — the state a checkbox cannot show. */
export type NotificationStatus = 'ON' | 'OFF' | 'NO_RELAY';

export interface NotificationStatusRow {
  type: EmailTemplateType;
  label: string;
  description: string;
  enabled: boolean;
  status: NotificationStatus;
}

export interface EmailSettingsResponse {
  productName: string | null;
  logoUrl: string | null;
  accentColor: string | null;
  supportUrl: string | null;
  footerNote: string | null;
  welcomeEnabled: boolean;
  newDeviceEnabled: boolean;
  signInNoticeEnabled: boolean;
  magicLinkEnabled: boolean;
  magicLinkRegistrationEnabled: boolean;
  magicLinkTtlMinutes: number;
  /** Whether the SMTP relay could actually send right now. */
  deliveryReady: boolean;
  /** Derived from the OIDC issuer, so links in email are absolute. Read-only here. */
  portalUrl: string;
  updatedBy: string | null;
  updatedAt: string | null;
  /** Rendered from the server's own enum rather than a copy of the list kept here. */
  notifications: NotificationStatusRow[];
}

export interface UpdateEmailSettingsRequest {
  productName?: string | null;
  logoUrl?: string | null;
  accentColor?: string | null;
  supportUrl?: string | null;
  footerNote?: string | null;
  welcomeEnabled: boolean;
  newDeviceEnabled: boolean;
  signInNoticeEnabled: boolean;
  magicLinkEnabled: boolean;
  magicLinkRegistrationEnabled: boolean;
  magicLinkTtlMinutes: number;
}

@Injectable({ providedIn: 'root' })
export class EmailService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/admin/email';

  getSettings(): Observable<EmailSettingsResponse> {
    return this.http.get<EmailSettingsResponse>(this.baseUrl);
  }

  saveSettings(req: UpdateEmailSettingsRequest): Observable<EmailSettingsResponse> {
    return this.http.put<EmailSettingsResponse>(this.baseUrl, req);
  }

  /** Sends one template with sample data, regardless of whether that notification is switched on. */
  sendPreview(
    type: EmailTemplateType,
    recipient: string,
  ): Observable<{ success: boolean; message: string }> {
    return this.http.post<{ success: boolean; message: string }>(`${this.baseUrl}/preview`, {
      type,
      recipient,
    });
  }
}
