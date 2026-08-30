import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, HostListener, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../../core/services/auth.service';
import { SnackbarService } from '../../../../core/services/snackbar.service';
import { Badge } from '../../../../shared/components/badge/badge';
import { PortalRole } from '../../../roles/models/role.model';
import { RoleService } from '../../../roles/services/role.service';
import { CreateUserRequest, PortalUser } from '../../models/portal-user.model';
import { UserService } from '../../services/user.service';

const INPUT_BASE_CLASSES =
  'block w-full rounded-lg border px-3.5 py-2.5 text-sm text-ink-900 shadow-sm transition-colors placeholder:text-ink-400 focus:outline-none focus:ring-2';
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
  private readonly roleService = inject(RoleService);
  private readonly authService = inject(AuthService);
  private readonly snackbarService = inject(SnackbarService);
  private readonly fb = inject(FormBuilder);

  /** Fetched rather than hardcoded, so a role an operator defines is immediately assignable here. */
  readonly availableRoles = signal<PortalRole[]>([]);

  /** The user whose roles are being edited, plus the set currently ticked in the dialog. */
  readonly editingRolesFor = signal<PortalUser | null>(null);
  readonly draftRoles = signal<Set<string>>(new Set());
  readonly savingRoles = signal(false);
  readonly rolesError = signal<string | null>(null);

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
    this.roleService.list().subscribe({
      next: (roles) => this.availableRoles.set(roles),
      // Losing the list degrades role editing but not the rest of the screen, so it stays quiet.
      error: () => this.availableRoles.set([]),
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.editingRolesFor()) {
      this.closeRolesModal();
    } else if (this.showCreateModal()) {
      this.closeCreateModal();
    }
  }

  openRolesModal(user: PortalUser): void {
    this.editingRolesFor.set(user);
    this.draftRoles.set(new Set(user.roles));
    this.rolesError.set(null);
  }

  closeRolesModal(): void {
    this.editingRolesFor.set(null);
    this.rolesError.set(null);
  }

  toggleDraftRole(name: string): void {
    this.draftRoles.update((current) => {
      const next = new Set(current);
      if (next.has(name)) {
        next.delete(name);
      } else {
        next.add(name);
      }
      return next;
    });
  }

  /**
   * Mirrors the server's refusal so the button explains itself before the request is sent. The
   * server still enforces it — this is a courtesy, not the control.
   */
  wouldDemoteSelf(): boolean {
    const user = this.editingRolesFor();
    return (
      !!user &&
      user.email === this.currentUserEmail &&
      user.roles.includes('ROLE_ADMIN') &&
      !this.draftRoles().has('ROLE_ADMIN')
    );
  }

  saveRoles(): void {
    const user = this.editingRolesFor();
    if (!user) {
      return;
    }

    this.savingRoles.set(true);
    this.rolesError.set(null);

    this.userService.setRoles(user.id, [...this.draftRoles()]).subscribe({
      next: (updated) => {
        this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
        this.savingRoles.set(false);
        this.editingRolesFor.set(null);
        this.snackbarService.success('Roles Updated', `${updated.email} now holds ${updated.roles.join(', ')}.`);
      },
      error: (err: HttpErrorResponse) => {
        this.savingRoles.set(false);
        const message = err.error?.message ?? 'Could not update roles.';
        this.rolesError.set(message);
        this.snackbarService.error('Update Failed', message, err.error?.code);
      },
    });
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
        this.snackbarService.error('Error Loading Users', 'Unable to retrieve user directory from backend.', 'PRTL-5000');
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
        this.snackbarService.success(
          'User Created Successfully',
          `Created account for ${newUser.email} with ${newUser.roles.join(', ')} privileges.`
        );
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        const serverCode = err.error?.code;
        const serverMsg = err.error?.message;

        if (err.status === 409) {
          const msg = serverMsg || 'A user with that email already exists.';
          this.createError.set(msg);
          this.snackbarService.error('Email Conflict', msg, serverCode || 'PRTL-2003');
        } else if (err.status === 400) {
          const msg = serverMsg || 'Please check form fields and password requirements.';
          this.createError.set(msg);
          this.snackbarService.warning('Validation Failed', msg, serverCode || 'PRTL-4001');
        } else {
          const msg = 'An unexpected error occurred while creating the user.';
          this.createError.set(msg);
          this.snackbarService.error('Creation Failed', msg, serverCode || 'PRTL-5000');
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
        this.snackbarService.info(
          'User Status Updated',
          `${updated.email} is now ${updated.enabled ? 'Enabled' : 'Disabled'}.`
        );
      },
      error: (err: HttpErrorResponse) => {
        const msg = err.error?.message || 'Could not update that user — please try again.';
        this.error.set(msg);
        this.updatingId.set(null);
        this.snackbarService.error('Update Failed', msg, err.error?.code);
      },
    });
  }

  /**
   * Clears a lockout applied after too many failed sign-ins. Without this, a locked-out user could
   * only be recovered by editing the database directly.
   */
  unlock(user: PortalUser): void {
    this.error.set(null);
    this.updatingId.set(user.id);
    this.userService.unlock(user.id).subscribe({
      next: (updated) => {
        this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
        this.updatingId.set(null);
        this.snackbarService.info('Account Unlocked', `${updated.email} can sign in again.`);
      },
      error: (err: HttpErrorResponse) => {
        const msg = err.error?.message || 'Could not unlock that user — please try again.';
        this.error.set(msg);
        this.updatingId.set(null);
        this.snackbarService.error('Unlock Failed', msg, err.error?.code);
      },
    });
  }

  /**
   * Administratively resets and disables MFA for a user who lost their device.
   */
  resetMfa(user: PortalUser): void {
    if (!confirm(`Are you sure you want to reset and disable Two-Factor Authentication for ${user.email}?`)) {
      return;
    }
    this.error.set(null);
    this.updatingId.set(user.id);
    this.userService.resetMfa(user.id).subscribe({
      next: (updated) => {
        this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
        this.updatingId.set(null);
        this.snackbarService.info('MFA Reset', `Two-Factor Authentication disabled for ${updated.email}.`);
      },
      error: (err: HttpErrorResponse) => {
        const msg = err.error?.message || 'Could not reset MFA for that user.';
        this.error.set(msg);
        this.updatingId.set(null);
        this.snackbarService.error('MFA Reset Failed', msg, err.error?.code);
      },
    });
  }
}
