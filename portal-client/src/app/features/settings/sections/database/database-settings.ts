import { KeyValuePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  DatabaseMigrationService,
  DatabaseStatusResponse,
  MigrateDatabaseResponse,
  TestConnectionResponse,
} from '../../../../core/services/database-migration.service';
import { SnackbarService } from '../../../../core/services/snackbar.service';

/** Current database runtime and the in-app migration from embedded H2 to PostgreSQL or MySQL. */
@Component({
  selector: 'app-database-settings',
  standalone: true,
  imports: [FormsModule, KeyValuePipe],
  templateUrl: './database-settings.html',
})
export class DatabaseSettings {
  readonly dbMigrationService = inject(DatabaseMigrationService);
  private readonly snackbarService = inject(SnackbarService);

  readonly dbStatus = signal<DatabaseStatusResponse | null>(null);
  readonly loadingDbStatus = signal(false);
  readonly targetDbType = signal<'POSTGRESQL' | 'MYSQL'>('POSTGRESQL');
  readonly targetHost = signal('localhost');
  readonly targetPort = signal(5432);
  readonly targetDbName = signal('portalsso');
  readonly targetUsername = signal('portal');
  readonly targetPassword = signal('');
  readonly customJdbcUrl = signal('');
  readonly saveConfiguration = signal(true);

  readonly testingConnection = signal(false);
  readonly testResult = signal<TestConnectionResponse | null>(null);

  readonly showMigrateConfirmModal = signal(false);
  readonly migrating = signal(false);
  readonly migrationResult = signal<MigrateDatabaseResponse | null>(null);
  readonly migrationError = signal<string | null>(null);
  readonly restarting = signal(false);
  readonly restartCountdown = signal(6);

  constructor() {
    this.loadDbStatus();
  }

  loadDbStatus(): void {
    this.loadingDbStatus.set(true);
    this.dbMigrationService.getStatus().subscribe({
      next: (res) => {
        this.dbStatus.set(res);
        this.loadingDbStatus.set(false);
      },
      error: () => {
        this.loadingDbStatus.set(false);
      },
    });
  }

  setTargetDbType(type: 'POSTGRESQL' | 'MYSQL'): void {
    this.targetDbType.set(type);
    if (type === 'POSTGRESQL') {
      this.targetPort.set(5432);
    } else {
      this.targetPort.set(3306);
    }
    this.testResult.set(null);
  }

  runTestConnection(): void {
    this.testingConnection.set(true);
    this.testResult.set(null);
    this.dbMigrationService
      .testConnection({
        databaseType: this.targetDbType(),
        host: this.targetHost().trim(),
        port: Number(this.targetPort()),
        databaseName: this.targetDbName().trim(),
        username: this.targetUsername().trim(),
        password: this.targetPassword(),
        customJdbcUrl: this.customJdbcUrl().trim() || undefined,
      })
      .subscribe({
        next: (res) => {
          this.testingConnection.set(false);
          this.testResult.set(res);
          if (res.success) {
            this.snackbarService.success('Connection Successful', `Connected to ${res.databaseProduct ?? this.targetDbType()} database.`);
          } else {
            this.snackbarService.error('Connection Failed', res.message);
          }
        },
        error: (err) => {
          this.testingConnection.set(false);
          const errorMsg = err.error?.message || 'Failed to test database connection.';
          this.testResult.set({ success: false, message: errorMsg });
          this.snackbarService.error('Connection Error', errorMsg);
        },
      });
  }

  openMigrateModal(): void {
    this.migrationError.set(null);
    this.showMigrateConfirmModal.set(true);
  }

  closeMigrateModal(): void {
    if (!this.migrating()) {
      this.showMigrateConfirmModal.set(false);
    }
  }

  executeMigration(): void {
    this.migrating.set(true);
    this.migrationError.set(null);
    this.migrationResult.set(null);

    this.dbMigrationService
      .migrate({
        databaseType: this.targetDbType(),
        host: this.targetHost().trim(),
        port: Number(this.targetPort()),
        databaseName: this.targetDbName().trim(),
        username: this.targetUsername().trim(),
        password: this.targetPassword(),
        customJdbcUrl: this.customJdbcUrl().trim() || undefined,
        saveConfiguration: this.saveConfiguration(),
      })
      .subscribe({
        next: (res) => {
          this.migrating.set(false);
          this.migrationResult.set(res);
          this.showMigrateConfirmModal.set(false);
          this.loadDbStatus();
          this.snackbarService.success('Migration Completed', `Successfully migrated ${res.totalRowsMigrated} records to ${this.targetDbType()}!`);
        },
        error: (err) => {
          this.migrating.set(false);
          const msg = err.error?.message || 'Migration encountered an error. Check server logs.';
          this.migrationError.set(msg);
          this.snackbarService.error('Migration Failed', msg);
        },
      });
  }

  restartApplication(): void {
    if (this.restarting()) return;
    this.restarting.set(true);
    this.restartCountdown.set(6);

    this.dbMigrationService.restart().subscribe({
      next: () => {
        this.runRestartCountdown();
      },
      error: () => {
        // Even if HTTP connection drops due to server shutdown, start the reload countdown
        this.runRestartCountdown();
      },
    });
  }

  private runRestartCountdown(): void {
    const interval = setInterval(() => {
      this.restartCountdown.update((c) => c - 1);
      if (this.restartCountdown() <= 0) {
        clearInterval(interval);
        window.location.reload();
      }
    }, 1000);
  }
}
