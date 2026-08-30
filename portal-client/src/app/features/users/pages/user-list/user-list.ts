import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/services/auth.service';
import { Badge } from '../../../../shared/components/badge/badge';
import { CreateUserRequest, PortalUser } from '../../models/portal-user.model';
import { UserService } from '../../services/user.service';

const INPUT_BASE_CLASSES =
  'block w-full rounded-lg border px-3.5 py-2 text-sm text-ink-900 shadow-sm transition-colors placeholder:text-ink-400 focus:outline-none focus:ring-2';
const INPUT_VALID_CLASSES = 'border-ink-300 focus:border-brand-500 focus:ring-brand-500/25';
const INPUT_INVALID_CLASSES = 'border-red-400 focus:border-red-400 focus:ring-red-400/25';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [Badge, DatePipe, ReactiveFormsModule],
  templateUrl: './user-list.html',
})
export class UserList {
  private readonly userService = inject(UserService);
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly users = signal<PortalUser[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly updatingId = signal<string | null>(null);

  // Modal signals
  readonly showCreateModal = signal(false);
  readonly submitting = signal(false);
  readonly createError = signal<string | null>(null);

  readonly currentUserEmail = this.authService.currentUser()?.email ?? null;

  readonly createForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    firstName: [''],
    lastName: [''],
    isAdmin: [false],
  });

  constructor() {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading.set(true);
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

  openCreateModal(): void {
    this.createForm.reset({ email: '', password: '', firstName: '', lastName: '', isAdmin: false });
    this.createError.set(null);
    this.showCreateModal.set(true);
  }

  closeCreateModal(): void {
    this.showCreateModal.set(false);
    this.createError.set(null);
  }

  inputClasses(controlName: 'email' | 'password'): string {
    const control = this.createForm.controls[controlName];
    const invalid = control.touched && control.invalid;
    return `${INPUT_BASE_CLASSES} ${invalid ? INPUT_INVALID_CLASSES : INPUT_VALID_CLASSES}`;
  }

  submitCreate(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    const val = this.createForm.getRawValue();
    const roles = val.isAdmin ? ['ROLE_ADMIN', 'ROLE_USER'] : ['ROLE_USER'];

    const req: CreateUserRequest = {
      email: val.email.trim(),
      password: val.password,
      firstName: val.firstName.trim() || undefined,
      lastName: val.lastName.trim() || undefined,
      roles,
      enabled: true,
    };

    this.submitting.set(true);
    this.createError.set(null);

    this.userService.create(req).subscribe({
      next: (newUser) => {
        this.submitting.set(false);
        this.showCreateModal.set(false);
        this.users.update((list) => [newUser, ...list]);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        if (err.status === 409) {
          this.createError.set('A user with that email already exists.');
        } else if (err.status === 400) {
          this.createError.set('Please check form fields and password requirements.');
        } else {
          this.createError.set('An error occurred creating the user.');
        }
      },
    });
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
