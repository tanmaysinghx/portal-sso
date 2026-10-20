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
    path: 'download',
    loadComponent: () => import('./features/download/download').then((m) => m.Download),
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
    path: 'forgot-password',
    loadComponent: () =>
      import('./features/auth/forgot-password/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./features/auth/reset-password/reset-password').then((m) => m.ResetPassword),
  },
  {
    path: 'forbidden',
    loadComponent: () => import('./features/auth/forbidden/forbidden').then((m) => m.Forbidden),
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell/shell').then((m) => m.Shell),
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'apps',
        loadComponent: () => import('./features/apps/pages/user-apps/user-apps').then((m) => m.UserApps),
      },
      {
        path: 'account',
        loadComponent: () => import('./features/account/account').then((m) => m.Account),
      },
      {
        path: 'sessions',
        loadComponent: () => import('./features/sessions/sessions').then((m) => m.Sessions),
      },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
        canActivate: [adminGuard],
      },
      {
        path: 'applications',
        loadComponent: () =>
          import('./features/applications/pages/application-list/application-list').then(
            (m) => m.ApplicationList,
          ),
        canActivate: [adminGuard],
      },
      {
        path: 'access-requests',
        loadComponent: () =>
          import('./features/access-requests/pages/access-request-list/access-request-list').then(
            (m) => m.AccessRequestList,
          ),
        canActivate: [adminGuard],
      },
      {
        path: 'clients',
        loadComponent: () =>
          import('./features/clients/pages/client-list/client-list').then((m) => m.ClientList),
        canActivate: [adminGuard],
      },
      {
        path: 'clients/new',
        loadComponent: () =>
          import('./features/clients/pages/client-create/client-create').then((m) => m.ClientCreate),
        canActivate: [adminGuard],
      },
      {
        path: 'users',
        loadComponent: () =>
          import('./features/users/pages/user-list/user-list').then((m) => m.UserList),
        canActivate: [adminGuard],
      },
      {
        path: 'roles',
        loadComponent: () =>
          import('./features/roles/pages/role-list/role-list').then((m) => m.RoleList),
        canActivate: [adminGuard],
      },
      {
        path: 'audit',
        loadComponent: () =>
          import('./features/audit/pages/audit-list/audit-list').then((m) => m.AuditList),
        canActivate: [adminGuard],
      },
      {
        path: 'settings',
        loadChildren: () => import('./features/settings/settings.routes').then((m) => m.SETTINGS_ROUTES),
        canActivate: [adminGuard],
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
