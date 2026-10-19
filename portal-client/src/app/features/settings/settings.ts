import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';

interface SettingsSection {
  path: string;
  label: string;
  description: string;
}

interface SettingsGroup {
  label: string;
  sections: SettingsSection[];
}

/**
 * Server-wide configuration, one sub-route per section. Personal security (password, two-factor)
 * is deliberately not here — it belongs to every user, not only administrators, and lives on the
 * My Account page.
 */
export const SETTINGS_GROUPS: SettingsGroup[] = [
  {
    label: 'Organization',
    sections: [
      { path: 'branding', label: 'Branding', description: 'Organization name and logo shown across the console.' },
    ],
  },
  {
    label: 'Email',
    sections: [
      { path: 'email-delivery', label: 'Delivery (SMTP)', description: 'The relay this server sends mail through.' },
      {
        path: 'email-templates',
        label: 'Templates & notifications',
        description: 'Email branding, which notifications are sent, and passwordless sign-in.',
      },
    ],
  },
  {
    label: 'Identity',
    sections: [
      { path: 'protocol', label: 'OIDC & tokens', description: 'Endpoints, token lifetimes and signing keys for integrators.' },
    ],
  },
  {
    label: 'Platform',
    sections: [
      { path: 'database', label: 'Database', description: 'Current database and migration to PostgreSQL or MySQL.' },
      { path: 'system', label: 'System', description: 'Versions and runtime stack.' },
    ],
  },
];

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './settings.html',
})
export class Settings {
  private readonly router = inject(Router);

  readonly groups = SETTINGS_GROUPS;

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map(() => this.router.url),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  readonly activeSection = computed(() => {
    const segment = this.url().split('?')[0].split('/')[2] ?? '';
    return this.groups.flatMap((g) => g.sections).find((s) => s.path === segment) ?? null;
  });
}
