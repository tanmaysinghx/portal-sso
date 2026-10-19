import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BrandingService } from '../../../../core/services/branding.service';
import { SnackbarService } from '../../../../core/services/snackbar.service';

/** Console branding: organization name and logo, kept in this browser by {@link BrandingService}. */
@Component({
  selector: 'app-branding-settings',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './branding-settings.html',
})
export class BrandingSettings {
  readonly brandingService = inject(BrandingService);
  private readonly snackbarService = inject(SnackbarService);

  readonly companyNameInput = signal(this.brandingService.companyName() ?? '');
  readonly companyLogoUrlInput = signal(this.brandingService.companyLogoUrl() ?? '');
  readonly uploadError = signal<string | null>(null);

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) {
      return;
    }

    const file = input.files[0];
    if (!file.type.startsWith('image/')) {
      this.uploadError.set('Please select a valid image file (PNG, SVG, JPG, WebP).');
      return;
    }

    if (file.size > 2 * 1024 * 1024) {
      this.uploadError.set('Image file size must be less than 2MB.');
      return;
    }

    this.uploadError.set(null);
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        this.companyLogoUrlInput.set(reader.result);
      }
    };
    reader.readAsDataURL(file);
  }

  saveBranding(): void {
    const name = this.companyNameInput().trim() || null;
    const logo = this.companyLogoUrlInput().trim() || null;

    this.brandingService.saveBranding(name, logo);

    if (logo) {
      this.snackbarService.success(
        'Branding Updated',
        `Custom logo and organization name "${name || 'Portal SSO'}" saved successfully.`
      );
    } else {
      this.snackbarService.info('Branding Updated', 'Organization branding updated.');
    }
  }

  resetBranding(): void {
    this.companyNameInput.set('');
    this.companyLogoUrlInput.set('');
    this.brandingService.resetToDefault();
    this.snackbarService.info('Branding Reset', 'Restored default Portal SSO logo and title.');
  }
}
