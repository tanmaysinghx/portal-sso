import { Injectable, computed, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';

export interface BrandingConfig {
  companyName: string | null;
  companyLogoUrl: string | null;
}

const STORAGE_KEY = 'portal_sso_branding';
const DEFAULT_TITLE = 'Portal SSO';
const DEFAULT_FAVICON = 'logo.svg';

@Injectable({ providedIn: 'root' })
export class BrandingService {
  private readonly titleService = inject(Title);

  readonly companyName = signal<string | null>(null);
  readonly companyLogoUrl = signal<string | null>(null);
  readonly showModal = signal(false);

  readonly hasCustomLogo = computed(() => !!this.companyLogoUrl());
  readonly hasCustomBranding = computed(() => !!(this.companyName() || this.companyLogoUrl()));

  constructor() {
    this.loadFromStorage();
    this.applyDocumentHeadBranding();
  }

  private loadFromStorage(): void {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const parsed: BrandingConfig = JSON.parse(raw);
        this.companyName.set(parsed.companyName || null);
        this.companyLogoUrl.set(parsed.companyLogoUrl || null);
      }
    } catch {
      // Ignore parse failure and keep defaults
    }
  }

  saveBranding(companyName: string | null, companyLogoUrl: string | null): void {
    const name = companyName?.trim() || null;
    const logo = companyLogoUrl?.trim() || null;

    this.companyName.set(name);
    this.companyLogoUrl.set(logo);

    try {
      const config: BrandingConfig = { companyName: name, companyLogoUrl: logo };
      localStorage.setItem(STORAGE_KEY, JSON.stringify(config));
    } catch {
      // LocalStorage quota or access error
    }

    this.applyDocumentHeadBranding();
  }

  resetToDefault(): void {
    this.companyName.set(null);
    this.companyLogoUrl.set(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Ignore
    }

    this.applyDocumentHeadBranding();
  }

  private applyDocumentHeadBranding(): void {
    // 1. Update Browser Tab Title
    const name = this.companyName();
    if (name) {
      this.titleService.setTitle(`${name} | Portal SSO`);
    } else {
      this.titleService.setTitle(DEFAULT_TITLE);
    }

    // 2. Update Browser Tab Favicon
    const logo = this.companyLogoUrl();
    const faviconElement = document.getElementById('app-favicon') as HTMLLinkElement | null;
    const faviconAlt = document.getElementById('app-favicon-alt') as HTMLLinkElement | null;

    if (faviconElement) {
      faviconElement.href = logo || DEFAULT_FAVICON;
    }
    if (faviconAlt) {
      faviconAlt.href = logo || 'favicon.ico';
    }
  }

  openModal(): void {
    this.showModal.set(true);
  }

  closeModal(): void {
    this.showModal.set(false);
  }
}
