import { Routes } from '@angular/router';

/** Old `?tab=` values, so bookmarks and links into the single-page Settings keep working. */
const LEGACY_TABS: Record<string, string> = {
  branding: 'branding',
  email: 'email-delivery',
  templates: 'email-templates',
  database: 'database',
  endpoints: 'protocol',
  security: 'protocol',
  system: 'system',
};

export const SETTINGS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./settings').then((m) => m.Settings),
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: ({ queryParams }) => LEGACY_TABS[queryParams['tab']] ?? 'branding',
      },
      {
        path: 'branding',
        loadComponent: () => import('./sections/branding/branding-settings').then((m) => m.BrandingSettings),
      },
      {
        path: 'email-delivery',
        loadComponent: () =>
          import('./sections/email-delivery/email-delivery-settings').then((m) => m.EmailDeliverySettings),
      },
      {
        path: 'email-templates',
        loadComponent: () =>
          import('./sections/email-templates/email-templates-settings').then((m) => m.EmailTemplatesSettings),
      },
      {
        path: 'protocol',
        loadComponent: () => import('./sections/protocol/protocol-settings').then((m) => m.ProtocolSettings),
      },
      {
        path: 'database',
        loadComponent: () => import('./sections/database/database-settings').then((m) => m.DatabaseSettings),
      },
      {
        path: 'system',
        loadComponent: () => import('./sections/system/system-settings').then((m) => m.SystemSettings),
      },
      {
        path: 'retention',
        loadComponent: () => import('./sections/retention/retention-settings').then((m) => m.RetentionSettingsComponent),
      },
      {
        path: 'registration',
        loadComponent: () => import('./sections/registration/registration-settings').then((m) => m.RegistrationSettingsComponent),
      },
      { path: '**', redirectTo: 'branding' },
    ],
  },
];
