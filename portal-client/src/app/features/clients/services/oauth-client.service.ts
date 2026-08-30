import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateOAuthClientRequest,
  OAuthClient,
  UpdateOAuthClientRequest,
} from '../models/oauth-client.model';

@Injectable({ providedIn: 'root' })
export class OAuthClientService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/admin/oauth-clients';

  list(): Observable<OAuthClient[]> {
    return this.http.get<OAuthClient[]>(this.baseUrl);
  }

  create(request: CreateOAuthClientRequest): Observable<OAuthClient> {
    return this.http.post<OAuthClient>(this.baseUrl, request);
  }

  update(id: string, request: UpdateOAuthClientRequest): Observable<OAuthClient> {
    return this.http.put<OAuthClient>(`${this.baseUrl}/${id}`, request);
  }

  /** Also revokes every token and consent issued under the client, server-side. */
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
