import { Routes } from '@angular/router';
import { adminGuard } from './core/guards/admin.guard';
import { authGuard } from './core/guards/auth.guard';

// Every route is lazily loaded. Eagerly importing these put the whole admin console — plus the
// public product page — into the initial bundle, so an unauthenticated visitor landing on
// /sign-in downloaded screens they had no access to and might never open. The guard stays a
// static import: it has to run before any chunk is fetched, and it is tiny.
export const routes: Routes = [
  // Not "/login" — that path is portal-server's own POST endpoint (proxied straight through
  // in dev, see proxy.conf.json); a client-side route there would never be reachable.
  {
    path: 'product',
    loadComponent: () => import('./features/product/product').then((m) => m.Product),
  },
  {
    path: 'docs',
    loadComponent: () => import('./features/docs/docs').then((m) => m.Docs),
  },
  {
    path: 'sign-in',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'sign-up',
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: 'apps',
    loadComponent: () => import('./features/apps/pages/user-apps/user-apps').then((m) => m.UserApps),
    canActivate: [authGuard],
  },
  {
    path: 'forbidden',
    loadComponent: () => import('./features/auth/forbidden/forbidden').then((m) => m.Forbidden),
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell/shell').then((m) => m.Shell),
    canActivate: [adminGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'applications',
        loadComponent: () =>
          import('./features/applications/pages/application-list/application-list').then(
            (m) => m.ApplicationList,
          ),
      },
      {
        path: 'clients',
        loadComponent: () =>
          import('./features/clients/pages/client-list/client-list').then((m) => m.ClientList),
      },
      {
        path: 'clients/new',
        loadComponent: () =>
          import('./features/clients/pages/client-create/client-create').then((m) => m.ClientCreate),
      },
      {
        path: 'users',
        loadComponent: () =>
          import('./features/users/pages/user-list/user-list').then((m) => m.UserList),
      },
      {
        path: 'roles',
        loadComponent: () =>
          import('./features/roles/pages/role-list/role-list').then((m) => m.RoleList),
      },
      {
        path: 'audit',
        loadComponent: () =>
          import('./features/audit/pages/audit-list/audit-list').then((m) => m.AuditList),
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/settings/settings').then((m) => m.Settings),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
