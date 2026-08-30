import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateOAuthClientRequest, OAuthClient } from '../models/oauth-client.model';

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
}
