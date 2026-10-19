import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RetentionSettingsService } from '../../../../core/services/retention-settings.service';
import { SnackbarService } from '../../../../core/services/snackbar.service';

@Component({
  selector: 'app-retention-settings',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './retention-settings.html',
})
export class RetentionSettingsComponent implements OnInit {
  private readonly retentionService = inject(RetentionSettingsService);
  private readonly snackbar = inject(SnackbarService);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly loginEventsHours = signal(48);
  readonly auditEventsHours = signal(0);

  readonly options = [
    { hours: 0, label: 'Keep indefinitely' },
    { hours: 48, label: '2 days' },
    { hours: 168, label: '1 week' },
    { hours: 336, label: '2 weeks' },
    { hours: 720, label: '30 days (about 1 month)' },
    { hours: 2160, label: '90 days (about 3 months)' },
    { hours: 4320, label: '180 days (about 6 months)' },
    { hours: 8760, label: '365 days (1 year)' },
  ];

  ngOnInit(): void {
    this.retentionService.get().subscribe({
      next: (settings) => {
        this.loginEventsHours.set(settings.loginEventsHours);
        this.auditEventsHours.set(settings.auditEventsHours);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load retention settings. Refresh the page and try again.');
        this.loading.set(false);
      },
    });
  }

  save(): void {
    this.saving.set(true);
    this.error.set(null);
    this.retentionService.save({
      loginEventsHours: this.loginEventsHours(),
      auditEventsHours: this.auditEventsHours(),
    }).subscribe({
      next: (settings) => {
        this.loginEventsHours.set(settings.loginEventsHours);
        this.auditEventsHours.set(settings.auditEventsHours);
        this.saving.set(false);
        this.snackbar.success('Retention settings saved', 'The new periods will apply during the next scheduled cleanup.');
      },
      error: () => {
        this.saving.set(false);
        this.error.set('Could not save retention settings. Check your selections and try again.');
      },
    });
  }

  updateLoginEvents(value: number | string): void {
    this.loginEventsHours.set(Number(value));
  }

  updateAuditEvents(value: number | string): void {
    this.auditEventsHours.set(Number(value));
  }
}
