import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { AuthService } from '../../../../core/services/auth.service';
import { Badge } from '../../../../shared/components/badge/badge';
import { PortalUser } from '../../models/portal-user.model';
import { UserService } from '../../services/user.service';

@Component({
  selector: 'app-user-list',
  imports: [Badge, DatePipe],
  templateUrl: './user-list.html',
})
export class UserList {
  private readonly userService = inject(UserService);
  private readonly authService = inject(AuthService);

  readonly users = signal<PortalUser[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly updatingId = signal<string | null>(null);

  readonly currentUserEmail = this.authService.currentUser()?.email ?? null;

  constructor() {
    this.userService.list().subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load users.');
        this.loading.set(false);
      },
    });
  }

  displayName(user: PortalUser): string {
    const name = [user.firstName, user.lastName].filter(Boolean).join(' ');
    return name || user.email;
  }

  toggleEnabled(user: PortalUser): void {
    if (user.email === this.currentUserEmail) {
      return;
    }
    this.error.set(null);
    this.updatingId.set(user.id);
    this.userService.setEnabled(user.id, !user.enabled).subscribe({
      next: (updated) => {
        this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
        this.updatingId.set(null);
      },
      error: () => {
        this.error.set('Could not update that user — please try again.');
        this.updatingId.set(null);
      },
    });
  }
}
