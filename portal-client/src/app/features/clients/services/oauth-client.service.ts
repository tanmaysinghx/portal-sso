import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../../core/models/page.model';
import {
  CreateOAuthClientRequest,
  OAuthClient,
  OAuthClientCreated,
  UpdateOAuthClientRequest,
} from '../models/oauth-client.model';

@Injectable({ providedIn: 'root' })
export class OAuthClientService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/admin/oauth-clients';

  list(search = '', enabled: boolean | null = null, page = 0, size = 25): Observable<Page<OAuthClient>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (search.trim()) {
      params = params.set('search', search.trim());
    }
    if (enabled !== null) {
      params = params.set('enabled', enabled);
    }
    return this.http.get<Page<OAuthClient>>(this.baseUrl, { params });
  }

  /** Returns the client plus its secret, which the server will never show again. */
  create(request: CreateOAuthClientRequest): Observable<OAuthClientCreated> {
    return this.http.post<OAuthClientCreated>(this.baseUrl, request);
  }

  update(id: string, request: UpdateOAuthClientRequest): Observable<OAuthClient> {
    return this.http.put<OAuthClient>(`${this.baseUrl}/${id}`, request);
  }

  /** Also revokes every token and consent issued under the client, server-side. */
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
