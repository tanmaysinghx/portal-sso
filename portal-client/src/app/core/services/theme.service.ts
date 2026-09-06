import { Injectable, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private static readonly STORAGE_KEY = 'portal_sso_theme';
  readonly isDark = signal<boolean>(true);

  constructor() {
    const saved = localStorage.getItem(ThemeService.STORAGE_KEY);
    if (saved === 'light') {
      this.isDark.set(false);
    } else if (saved === 'dark') {
      this.isDark.set(true);
    } else {
      // Default to dark mode for auth/docs, or follow preference
      this.isDark.set(true);
    }
  }

  toggleTheme(): void {
    const next = !this.isDark();
    this.isDark.set(next);
    localStorage.setItem(ThemeService.STORAGE_KEY, next ? 'dark' : 'light');
  }

  setTheme(mode: ThemeMode): void {
    const dark = mode === 'dark';
    this.isDark.set(dark);
    localStorage.setItem(ThemeService.STORAGE_KEY, mode);
  }
}
