import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface DatabaseStatusResponse {
  databaseType: string;
  isEmbedded: boolean;
  jdbcUrl: string;
  userName: string;
  databaseProductName: string;
  databaseProductVersion: string;
  driverName: string;
  configFilePath?: string;
}

export interface TestConnectionRequest {
  databaseType: string;
  host?: string;
  port?: number;
  databaseName?: string;
  username?: string;
  password?: string;
  customJdbcUrl?: string;
}

export interface TestConnectionResponse {
  success: boolean;
  message: string;
  databaseProduct?: string;
  databaseVersion?: string;
  resolvedJdbcUrl?: string;
}

export interface MigrateDatabaseRequest {
  databaseType: string;
  host?: string;
  port?: number;
  databaseName?: string;
  username?: string;
  password?: string;
  customJdbcUrl?: string;
  saveConfiguration: boolean;
}

export interface MigrateDatabaseResponse {
  success: boolean;
  message: string;
  totalRowsMigrated: number;
  tablesMigrated: Record<string, number>;
  targetJdbcUrl: string;
  activationInstructions: string;
  configurationSaved?: boolean;
  configFilePath?: string;
}

@Injectable({ providedIn: 'root' })
export class DatabaseMigrationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/admin/database';

  getStatus(): Observable<DatabaseStatusResponse> {
    return this.http.get<DatabaseStatusResponse>(`${this.baseUrl}/status`);
  }

  testConnection(req: TestConnectionRequest): Observable<TestConnectionResponse> {
    return this.http.post<TestConnectionResponse>(`${this.baseUrl}/test-connection`, req);
  }

  migrate(req: MigrateDatabaseRequest): Observable<MigrateDatabaseResponse> {
    return this.http.post<MigrateDatabaseResponse>(`${this.baseUrl}/migrate`, req);
  }
}
