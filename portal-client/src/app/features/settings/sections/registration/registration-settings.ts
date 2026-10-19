import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RegistrationSettingsAdminService } from '../../../../core/services/registration-settings.service';
import { SnackbarService } from '../../../../core/services/snackbar.service';

@Component({
  selector: 'app-registration-settings',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './registration-settings.html',
})
export class RegistrationSettingsComponent implements OnInit {
  private readonly service = inject(RegistrationSettingsAdminService);
  private readonly snackbar = inject(SnackbarService);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly enabled = signal(false);
  readonly requiresApproval = signal(false);

  ngOnInit(): void {
    this.service.get().subscribe({
      next: settings => {
        this.enabled.set(settings.enabled);
        this.requiresApproval.set(settings.requiresApproval);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load registration settings. Refresh the page and try again.');
        this.loading.set(false);
      },
    });
  }

  save(): void {
    this.saving.set(true);
    this.error.set(null);
    this.service.save({ enabled: this.enabled(), requiresApproval: this.requiresApproval() }).subscribe({
      next: settings => {
        this.enabled.set(settings.enabled);
        this.requiresApproval.set(settings.requiresApproval);
        this.saving.set(false);
        this.snackbar.success('Registration settings saved', 'The sign-up page and registration API now use this policy.');
      },
      error: () => {
        this.saving.set(false);
        this.error.set('Could not save registration settings. Please try again.');
      },
    });
  }
}
