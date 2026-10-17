import { Component, DestroyRef, HostListener, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { map, switchMap } from 'rxjs';
import { SnackbarService } from '../../../../core/services/snackbar.service';
import { Badge } from '../../../../shared/components/badge/badge';
import { QuickstartModal } from '../../../../shared/components/quickstart-modal/quickstart-modal';
import { OAuthClient } from '../../../clients/models/oauth-client.model';
import { OAuthClientService } from '../../../clients/services/oauth-client.service';
import { PortalRole } from '../../../roles/models/role.model';
import { RoleService } from '../../../roles/services/role.service';
import {
  agentGuideFileName,
  buildAgentGuide,
  buildEnvFile,
  downloadTextFile,
} from '../../integration-snippets';
import {
  Application,
  ApplicationAccessType,
  ApplicationCredentials,
  CreateApplicationRequest,
  OnboardApplicationRequest,
  UpdateApplicationRequest,
} from '../../models/application.model';
import { ApplicationService } from '../../services/application.service';

/** How the app signs users in: a new OAuth client, an existing one, or none (bookmark only). */
type SsoMode = 'new' | 'existing' | 'none';

/** The one place every key, secret and endpoint for an app is shown. */
interface CredentialsView {
  app: Application;
  creds: ApplicationCredentials;
}

type AppFormValue = ReturnType<ApplicationList['form']['getRawValue']>;

const CATEGORY_PRESETS =['General', 'Productivity', 'Engineering', 'Operations', 'Finance', 'Communication'];

@Component({
  selector: 'app-application-list',
  standalone: true,
  imports: [Badge, ReactiveFormsModule, RouterLink, QuickstartModal],
  templateUrl: './application-list.html',
})
export class ApplicationList {
  private readonly applicationService = inject(ApplicationService);
  private readonly roleService = inject(RoleService);
  private readonly oauthClientService = inject(OAuthClientService);
  private readonly snackbarService = inject(SnackbarService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly applications = signal<Application[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly searchQuery = signal('');
  readonly selectedCategory = signal('all');
  readonly selectedStatus = signal<'all' | 'enabled' | 'disabled'>('all');

  readonly availableRoles = signal<PortalRole[]>([]);
  readonly availableOAuthClients = signal<OAuthClient[]>([]);

  readonly showModal = signal(false);
  readonly modalMode = signal<'create' | 'edit'>('create');
  readonly editingApp = signal<Application | null>(null);
  readonly submitting = signal(false);
  readonly modalError = signal<string | null>(null);
  readonly selectedRoleIds = signal<string[]>([]);

  readonly pendingDelete = signal<Application | null>(null);
  readonly deleting = signal(false);

  // Credentials view: the single place keys, secrets, endpoints and .env are shown.
  readonly credentialsView = signal<CredentialsView | null>(null);
  readonly credentialsLoadingId = signal<string | null>(null);
  readonly copiedKey = signal<string | null>(null);
  readonly rotateArmed = signal(false);
  readonly rotating = signal(false);
  readonly secretArmed = signal(false);
  readonly regeneratingSecret = signal(false);

  readonly envSnippet = computed(() => {
    const view = this.credentialsView();
    return view ? buildEnvFile(view.app.name, view.creds) : '';
  });

  /** True while a one-time secret is on screen; the modal then refuses casual dismissal. */
  readonly showsOneTimeSecret = computed(() => {
    const creds = this.credentialsView()?.creds;
    return !!(creds?.oidc?.clientSecret || creds?.externalAuth?.apiKey);
  });

  // Quickstart code generator
  readonly quickstart = signal<CredentialsView | null>(null);

  readonly categoryPresets = CATEGORY_PRESETS;

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    description: [''],
    appUrl: ['', [Validators.required, Validators.pattern(/^https?:\/\/.+/)]],
    iconUrl: [''],
    category: ['General', [Validators.required]],
    ssoMode: ['new' as SsoMode],
    clientId: [''],
    newClientId: ['', [Validators.pattern(/^[a-zA-Z0-9._-]*$/), Validators.maxLength(100)]],
    redirectUris: [''],
    profileScope: [true],
    emailScope: [true],
    confidential: [false],
    externalAuthEnabled: [false],
    externalRegistrationEnabled: [true],
    accessType: ['ALL_USERS' as ApplicationAccessType],
    enabled: [true],
    displayOrder: [0],
  });

  readonly categories = computed(() => {
    const set = new Set<string>(CATEGORY_PRESETS);
    for (const app of this.applications()) {
      if (app.category) set.add(app.category);
    }
    return Array.from(set).sort();
  });

  readonly filteredApplications = computed(() => {
    const q = this.searchQuery().toLowerCase().trim();
    const cat = this.selectedCategory();
    const stat = this.selectedStatus();

    return this.applications().filter((app) => {
      const matchesSearch =
        !q ||
        app.name.toLowerCase().includes(q) ||
        (app.description && app.description.toLowerCase().includes(q)) ||
        app.appUrl.toLowerCase().includes(q);
      const matchesCat = cat === 'all' || app.category.toLowerCase() === cat.toLowerCase();
      const matchesStat =
        stat === 'all' || (stat === 'enabled' && app.enabled) || (stat === 'disabled' && !app.enabled);
      return matchesSearch && matchesCat && matchesStat;
    });
  });

  readonly stats = computed(() => {
    const all = this.applications();
    return {
      total: all.length,
      enabled: all.filter((a) => a.enabled).length,
      restricted: all.filter((a) => a.accessType === 'RESTRICTED').length,
      categoriesCount: this.categories().length,
    };
  });

  constructor() {
    this.loadApplications();
    this.loadPrerequisites();

    // Prefill the new client's ID and redirect URI from the app until the admin edits them.
    this.form.controls.name.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((name) => {
      if (this.modalMode() === 'create' && !this.form.controls.newClientId.dirty) {
        this.form.controls.newClientId.setValue(this.toClientId(name || ''), { emitEvent: false });
      }
    });
    this.form.controls.appUrl.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((url) => {
      if (this.modalMode() === 'create' && !this.form.controls.redirectUris.dirty) {
        this.form.controls.redirectUris.setValue((url || '').trim(), { emitEvent: false });
      }
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.pendingDelete()) {
      this.pendingDelete.set(null);
    } else if (this.quickstart()) {
      this.quickstart.set(null);
    } else if (this.credentialsView() && !this.showsOneTimeSecret()) {
      this.closeCredentials();
    } else if (this.showModal()) {
      this.closeModal();
    }
  }

  loadApplications(): void {
    this.loading.set(true);
    this.error.set(null);
    this.applicationService.getAdminApplications().subscribe({
      next: (apps) => {
        this.applications.set(apps);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load applications.');
        this.loading.set(false);
      },
    });
  }

  private loadPrerequisites(): void {
    this.roleService.list().subscribe({
      next: (roles) => this.availableRoles.set(roles),
      error: () => {},
    });
    this.loadOAuthClients();
  }

  private loadOAuthClients(): void {
    this.oauthClientService.list('', null, 0, 100).subscribe({
      next: (page) => this.availableOAuthClients.set(page.content),
      error: () => {},
    });
  }

  // ---------------------------------------------------------------------------
  // Create / edit
  // ---------------------------------------------------------------------------

  openCreate(): void {
    this.modalMode.set('create');
    this.editingApp.set(null);
    this.modalError.set(null);
    this.selectedRoleIds.set([]);
    this.form.reset({
      name: '',
      description: '',
      appUrl: '',
      iconUrl: '',
      category: 'General',
      ssoMode: 'new',
      clientId: '',
      newClientId: '',
      redirectUris: '',
      profileScope: true,
      emailScope: true,
      confidential: false,
      externalAuthEnabled: false,
      externalRegistrationEnabled: true,
      accessType: 'ALL_USERS',
      enabled: true,
      displayOrder: 0,
    });
    this.showModal.set(true);
  }

  openEdit(app: Application): void {
    this.modalMode.set('edit');
    this.editingApp.set(app);
    this.modalError.set(null);
    this.selectedRoleIds.set(app.roles ? app.roles.map((r) => r.id) : []);
    this.form.reset({
      name: app.name,
      description: app.description ?? '',
      appUrl: app.appUrl,
      iconUrl: app.iconUrl ?? '',
      category: app.category,
      ssoMode: app.clientId ? 'existing' : 'none',
      clientId: app.clientId ?? '',
      newClientId: '',
      redirectUris: '',
      profileScope: true,
      emailScope: true,
      confidential: false,
      externalAuthEnabled: app.externalAuthEnabled,
      externalRegistrationEnabled: app.externalRegistrationEnabled ?? true,
      accessType: app.accessType,
      enabled: app.enabled,
      displayOrder: app.displayOrder,
    });
    this.showModal.set(true);
  }

  closeModal(): void {
    this.showModal.set(false);
    this.editingApp.set(null);
    this.modalError.set(null);
  }

  toggleRoleSelection(roleId: string): void {
    const current = this.selectedRoleIds();
    this.selectedRoleIds.set(current.includes(roleId) ? current.filter((id) => id !== roleId) : [...current, roleId]);
  }

  isRoleSelected(roleId: string): boolean {
    return this.selectedRoleIds().includes(roleId);
  }

  saveApplication(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const val = this.form.getRawValue();
    const accessType = val.accessType as ApplicationAccessType;
    const roleIds = accessType === 'RESTRICTED' ? this.selectedRoleIds() : [];

    if (accessType === 'RESTRICTED' && roleIds.length === 0) {
      this.modalError.set('Select at least one role for restricted access.');
      return;
    }
    if (val.ssoMode === 'existing' && !val.clientId) {
      this.modalError.set('Choose the OAuth client to link, or pick another sign-in option.');
      return;
    }

    const base: CreateApplicationRequest & UpdateApplicationRequest = {
      name: val.name.trim(),
      description: val.description.trim() || undefined,
      appUrl: val.appUrl.trim(),
      iconUrl: val.iconUrl.trim() || undefined,
      category: val.category.trim() || 'General',
      clientId: val.ssoMode === 'existing' ? val.clientId : undefined,
      accessType,
      roleIds,
      enabled: val.enabled,
      displayOrder: val.displayOrder,
      externalAuthEnabled: val.externalAuthEnabled,
      externalRegistrationEnabled: val.externalRegistrationEnabled,
    };

    this.submitting.set(true);
    this.modalError.set(null);

    if (this.modalMode() === 'create') {
      this.create(base, val);
    } else {
      this.update(base);
    }
  }

  private create(application: CreateApplicationRequest, val: AppFormValue): void {
    const request: OnboardApplicationRequest = { application };
    if (val.ssoMode === 'new') {
      request.oauthClient = {
        clientId: val.newClientId.trim() || undefined,
        redirectUris: this.parseLines(val.redirectUris),
        scopes: ['openid', ...(val.profileScope ? ['profile'] : []), ...(val.emailScope ? ['email'] : [])],
        confidential: val.confidential,
      };
    }

    // One atomic call: the server creates the client, the app and the API key together.
    this.applicationService.onboardApplication(request).subscribe({
      next: ({ application: created, credentials }) => {
        this.applications.set([created, ...this.applications()]);
        if (request.oauthClient) this.loadOAuthClients();
        this.submitting.set(false);
        this.closeModal();
        this.credentialsView.set({ app: created, creds: credentials });
        this.snackbarService.success(`Application "${created.name}" created.`);
      },
      error: (err) => {
        this.submitting.set(false);
        this.modalError.set(err?.error?.message ?? 'Failed to create application.');
      },
    });
  }

  private update(request: UpdateApplicationRequest): void {
    const current = this.editingApp();
    if (!current) return;

    this.applicationService.updateApplication(current.id, request).subscribe({
      next: (updated) => {
        this.applications.set(this.applications().map((a) => (a.id === updated.id ? updated : a)));
        this.submitting.set(false);
        this.closeModal();
        this.snackbarService.success(`Application "${updated.name}" updated.`);
        // External API just switched on without a key: take the admin straight to key generation.
        if (updated.externalAuthEnabled && !updated.externalAuthApiKeyIssued) {
          this.openCredentials(updated);
        }
      },
      error: (err) => {
        this.submitting.set(false);
        this.modalError.set(err?.error?.message ?? 'Failed to update application.');
      },
    });
  }

  // ---------------------------------------------------------------------------
  // Credentials, quickstart and agent guide
  // ---------------------------------------------------------------------------

  openCredentials(app: Application): void {
    this.loadCredentials(app, (creds) => this.credentialsView.set({ app, creds }));
  }

  closeCredentials(): void {
    this.credentialsView.set(null);
    this.rotateArmed.set(false);
    this.secretArmed.set(false);
    this.copiedKey.set(null);
  }

  openQuickstart(app: Application): void {
    this.loadCredentials(app, (creds) => this.quickstart.set({ app, creds }));
  }

  openQuickstartFromCredentials(): void {
    const view = this.credentialsView();
    if (view) this.quickstart.set(view);
  }

  downloadAgentGuide(app: Application): void {
    this.loadCredentials(app, (creds) => this.saveAgentGuide({ app, creds }));
  }

  downloadAgentGuideFromCredentials(): void {
    const view = this.credentialsView();
    if (view) this.saveAgentGuide(view);
  }

  private saveAgentGuide({ app, creds }: CredentialsView): void {
    downloadTextFile(agentGuideFileName(), buildAgentGuide(app.name, app.appUrl, creds));
    this.snackbarService.success(`Downloaded ${agentGuideFileName()} for "${app.name}". It contains no secrets.`);
  }

  private loadCredentials(app: Application, then: (creds: ApplicationCredentials) => void): void {
    this.credentialsLoadingId.set(app.id);
    this.applicationService.getCredentials(app.id).subscribe({
      next: (creds) => {
        this.credentialsLoadingId.set(null);
        then(creds);
      },
      error: (err) => {
        this.credentialsLoadingId.set(null);
        this.snackbarService.error(err?.error?.message ?? 'Could not load application credentials.');
      },
    });
  }

  /** First click arms, second click rotates: the current key stops working immediately. */
  rotateApiKey(): void {
    const view = this.credentialsView();
    if (!view) return;
    if (view.creds.externalAuth?.apiKeyIssued && !this.rotateArmed()) {
      this.rotateArmed.set(true);
      return;
    }

    this.rotating.set(true);
    this.applicationService
      .rotateExternalAuthApiKey(view.app.id)
      .pipe(
        switchMap((key) =>
          this.applicationService.getCredentials(view.app.id).pipe(
            map((creds) => ({ key, creds })),
          ),
        ),
      )
      .subscribe({
        next: ({ key, creds }) => {
          const app = { ...view.app, externalAuthEnabled: true, externalAuthApiKeyIssued: true, externalAuthApiKeyPrefix: key.apiKeyPrefix };
          this.applications.set(this.applications().map((a) => (a.id === app.id ? app : a)));
          this.credentialsView.set({
            app,
            creds: { ...creds, externalAuth: creds.externalAuth && { ...creds.externalAuth, apiKey: key.apiKey } },
          });
          this.rotating.set(false);
          this.rotateArmed.set(false);
          this.snackbarService.success(`New API key issued for "${app.name}". Copy it now.`);
        },
        error: (err) => {
          this.rotating.set(false);
          this.rotateArmed.set(false);
          this.snackbarService.error(err?.error?.message ?? 'Failed to generate API key.');
        },
      });
  }

  /**
   * Secrets are stored hashed, so a lost one cannot be shown again — it can only be replaced.
   * First click arms, second click regenerates; apps using the old secret stop authenticating.
   */
  regenerateClientSecret(): void {
    const view = this.credentialsView();
    if (!view) return;
    if (!this.secretArmed()) {
      this.secretArmed.set(true);
      return;
    }

    this.regeneratingSecret.set(true);
    this.applicationService.regenerateClientSecret(view.app.id).subscribe({
      next: (creds) => {
        // Keep a freshly issued API key on screen if one is showing.
        const apiKey = view.creds.externalAuth?.apiKey ?? null;
        this.credentialsView.set({
          app: view.app,
          creds: { ...creds, externalAuth: creds.externalAuth && { ...creds.externalAuth, apiKey } },
        });
        this.regeneratingSecret.set(false);
        this.secretArmed.set(false);
        this.snackbarService.success(`New client secret issued for "${view.app.name}". Copy it now.`);
      },
      error: (err) => {
        this.regeneratingSecret.set(false);
        this.secretArmed.set(false);
        this.snackbarService.error(err?.error?.message ?? 'Failed to regenerate client secret.');
      },
    });
  }

  copy(key: string, text: string | null | undefined): void {
    if (!text) return;
    navigator.clipboard.writeText(text).then(() => {
      this.copiedKey.set(key);
      setTimeout(() => {
        if (this.copiedKey() === key) this.copiedKey.set(null);
      }, 2000);
    });
  }

  // ---------------------------------------------------------------------------
  // Delete
  // ---------------------------------------------------------------------------

  confirmDelete(app: Application): void {
    this.pendingDelete.set(app);
  }

  closeDeleteModal(): void {
    this.pendingDelete.set(null);
  }

  executeDelete(): void {
    const app = this.pendingDelete();
    if (!app) return;

    this.deleting.set(true);
    this.applicationService.deleteApplication(app.id).subscribe({
      next: () => {
        this.applications.set(this.applications().filter((a) => a.id !== app.id));
        this.deleting.set(false);
        this.pendingDelete.set(null);
        this.snackbarService.success(`Application "${app.name}" deleted.`);
      },
      error: (err) => {
        this.deleting.set(false);
        this.snackbarService.error(err?.error?.message ?? 'Failed to delete application.');
      },
    });
  }

  getInitials(name: string): string {
    return name
      .split(' ')
      .slice(0, 2)
      .map((w) => w.charAt(0).toUpperCase())
      .join('');
  }

  private parseLines(value: string): string[] {
    return value
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => line.length > 0);
  }

  private toClientId(name: string): string {
    return name
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9._-]+/g, '-')
      .replace(/(^-+)|(-+$)/g, '')
      .slice(0, 80);
  }
}
