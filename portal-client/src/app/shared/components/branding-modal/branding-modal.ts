import { Component, HostListener, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BrandingService } from '../../../core/services/branding.service';
import { SnackbarService } from '../../../core/services/snackbar.service';

@Component({
  selector: 'app-branding-modal',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './branding-modal.html',
})
export class BrandingModal {
  readonly brandingService = inject(BrandingService);
  private readonly snackbarService = inject(SnackbarService);

  readonly companyNameInput = signal('');
  readonly companyLogoUrlInput = signal('');
  readonly uploadError = signal<string | null>(null);

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.brandingService.showModal()) {
      this.close();
    }
  }

  constructor() {
    // Populate form with current values
    this.companyNameInput.set(this.brandingService.companyName() ?? '');
    this.companyLogoUrlInput.set(this.brandingService.companyLogoUrl() ?? '');
  }

  open(): void {
    this.companyNameInput.set(this.brandingService.companyName() ?? '');
    this.companyLogoUrlInput.set(this.brandingService.companyLogoUrl() ?? '');
    this.uploadError.set(null);
    this.brandingService.openModal();
  }

  close(): void {
    this.brandingService.closeModal();
    this.uploadError.set(null);
  }

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

  save(): void {
    const name = this.companyNameInput().trim() || null;
    const logo = this.companyLogoUrlInput().trim() || null;

    this.brandingService.saveBranding(name, logo);
    this.brandingService.closeModal();

    if (logo) {
      this.snackbarService.success(
        'Custom Branding Applied',
        `Your company logo is now displayed alongside Portal SSO across the platform.`
      );
    } else {
      this.snackbarService.info('Branding Updated', 'Displaying standard Portal SSO logo.');
    }
  }

  reset(): void {
    this.companyNameInput.set('');
    this.companyLogoUrlInput.set('');
    this.brandingService.resetToDefault();
    this.brandingService.closeModal();
    this.snackbarService.info('Branding Reset', 'Restored default Portal SSO logo and name.');
  }
}
