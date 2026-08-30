import { Routes } from '@angular/router';
import { adminGuard } from './core/guards/admin.guard';
import { Forbidden } from './features/auth/forbidden/forbidden';
import { Login } from './features/auth/login/login';
import { Product } from './features/product/product';
import { ClientCreate } from './features/clients/pages/client-create/client-create';
import { ClientList } from './features/clients/pages/client-list/client-list';
import { Dashboard } from './features/dashboard/dashboard';
import { UserList } from './features/users/pages/user-list/user-list';
import { Shell } from './layout/shell/shell';

export const routes: Routes = [
  // Not "/login" — that path is portal-server's own POST endpoint (proxied straight through
  // in dev, see proxy.conf.json); a client-side route there would never be reachable.
  { path: 'product', component: Product },
  { path: 'sign-in', component: Login },
  { path: 'forbidden', component: Forbidden },
  {
    path: '',
    component: Shell,
    canActivate: [adminGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: Dashboard },
      { path: 'clients', component: ClientList },
      { path: 'clients/new', component: ClientCreate },
      { path: 'users', component: UserList },
    ],
  },
  { path: '**', redirectTo: '' },
];
