import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';
import { SnackbarService } from '../../../../core/services/snackbar.service';
import { Badge } from '../../../../shared/components/badge/badge';
import { OAuthClient } from '../../../clients/models/oauth-client.model';
import { OAuthClientService } from '../../../clients/services/oauth-client.service';
import { PortalRole } from '../../../roles/models/role.model';
import { RoleService } from '../../../roles/services/role.service';
import {
  Application,
  ApplicationAccessType,
  CreateApplicationRequest,
  UpdateApplicationRequest,
} from '../../models/application.model';
import { ApplicationService } from '../../services/application.service';

import { QuickstartModal } from '../../../../shared/components/quickstart-modal/quickstart-modal';

export interface CreatedAppSummary {
  appName: string;
  appUrl: string;
  clientId?: string;
  clientSecret?: string;
  externalApiKey?: string;
  headerName?: string;
  registerEndpoint?: string;
  loginEndpoint?: string;
  issuerUrl: string;
  redirectUris?: string[];
  scopes?: string[];
  envSnippet: string;
  isNew?: boolean;
}

const CATEGORY_PRESETS = ['General', 'Productivity', 'Engineering', 'Operations', 'Finance', 'Communication'];

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

  readonly pendingDelete = signal<Application | null>(null);
  readonly deleting = signal(false);
  readonly issuedClientId = signal<string | null>(null);
  readonly issuedClientSecret = signal<string | null>(null);
  readonly secretCopied = signal(false);
  readonly issuedAppApiKey = signal<{
    apiKey: string;
    headerName: string;
    registerEndpoint: string;
    loginEndpoint: string;
  } | null>(null);
  readonly appApiKeyCopied = signal(false);

  // Unified App Created Summary Modal
  readonly createdAppSummary = signal<CreatedAppSummary | null>(null);
  readonly appSummaryEnvCopied = signal(false);
  readonly appSummarySecretCopied = signal(false);
  readonly appSummaryApiKeyCopied = signal(false);
  readonly appSummaryClientIdCopied = signal(false);

  copyAppSummaryEnv(): void {
    const env = this.createdAppSummary()?.envSnippet;
    if (!env) return;
    navigator.clipboard.writeText(env).then(() => {
      this.appSummaryEnvCopied.set(true);
      setTimeout(() => this.appSummaryEnvCopied.set(false), 2000);
    });
  }

  copyAppSummarySecret(): void {
    const sec = this.createdAppSummary()?.clientSecret;
    if (!sec) return;
    navigator.clipboard.writeText(sec).then(() => {
      this.appSummarySecretCopied.set(true);
      setTimeout(() => this.appSummarySecretCopied.set(false), 2000);
    });
  }

  copyAppSummaryApiKey(): void {
    const key = this.createdAppSummary()?.externalApiKey;
    if (!key) return;
    navigator.clipboard.writeText(key).then(() => {
      this.appSummaryApiKeyCopied.set(true);
      setTimeout(() => this.appSummaryApiKeyCopied.set(false), 2000);
    });
  }

  copyAppSummaryClientId(): void {
    const cid = this.createdAppSummary()?.clientId;
    if (!cid) return;
    navigator.clipboard.writeText(cid).then(() => {
      this.appSummaryClientIdCopied.set(true);
      setTimeout(() => this.appSummaryClientIdCopied.set(false), 2000);
    });
  }

  dismissAppSummary(): void {
    this.createdAppSummary.set(null);
  }

  openQuickstartFromSummary(): void {
    const summary = this.createdAppSummary();
    if (!summary) return;
    const matchedApp = this.applications().find((a) => a.name === summary.appName) || {
      id: '',
      name: summary.appName,
      appUrl: summary.appUrl,
      clientId: summary.clientId,
      category: 'General',
      enabled: true,
      displayOrder: 0,
      createdAt: '',
      updatedAt: '',
    };
    this.dismissAppSummary();
    this.openQuickstart(matchedApp as Application);
  }

  // Quickstart modal
  readonly showQuickstart = signal(false);
  readonly selectedQuickstartApp = signal<Application | null>(null);

  openQuickstart(app: Application): void {
    this.selectedQuickstartApp.set(app);
    this.showQuickstart.set(true);
  }

  closeQuickstart(): void {
    this.showQuickstart.set(false);
    this.selectedQuickstartApp.set(null);
  }

  openAppCredentials(app: Application): void {
    const issuerUrl = typeof window !== 'undefined' ? window.location.origin : 'https://portal.tanmaysinghx.com';
    const envLines = [
      `# Portal SSO Configuration for ${app.name}`,
      `PORTAL_URL=${issuerUrl}`,
    ];
    if (app.clientId) {
      envLines.push(`PORTAL_CLIENT_ID=${app.clientId}`);
      envLines.push(`PORTAL_REDIRECT_URI=${issuerUrl}/oauth2/popup-callback`);
      envLines.push(`PORTAL_SCOPES=openid profile email`);
    }
    if (app.externalAuthEnabled) {
      if (app.externalAuthApiKeyPrefix) {
        envLines.push(`PORTAL_API_KEY=${app.externalAuthApiKeyPrefix}...`);
      } else {
        envLines.push(`# PORTAL_API_KEY=(generate key below)`);
      }
    }

    this.createdAppSummary.set({
      appName: app.name,
      appUrl: app.appUrl,
      clientId: app.clientId || undefined,
      externalApiKey: app.externalAuthApiKeyPrefix ? `${app.externalAuthApiKeyPrefix}...` : undefined,
      headerName: 'X-Portal-App-Key',
      issuerUrl,
      redirectUris: app.clientId ? [`${issuerUrl}/oauth2/popup-callback`, app.appUrl] : undefined,
      scopes: app.clientId ? ['openid', 'profile', 'email'] : undefined,
      envSnippet: envLines.join('\n'),
      isNew: false,
    });
  }

  downloadAgentInstructions(app: Application): void {
    const issuerUrl = typeof window !== 'undefined' ? window.location.origin : 'https://portal.tanmaysinghx.com';
    const clientId = app.clientId || 'your-client-id';
    const redirectUri = `${issuerUrl}/oauth2/popup-callback`;

    const content = `# Portal SSO Integration Instructions for ${app.name}

> Give this file directly to an AI agent (Cursor, Claude Code, GitHub Copilot, ChatGPT, or Antigravity):
> **"Integrate Portal SSO authentication into this repository following the instructions below."**

---

## 1. Credentials & Configuration

- **Portal URL**: \`${issuerUrl}\`
- **Client ID**: \`${clientId}\`
- **Popup Callback URI**: \`${redirectUri}\`
- **Application URL**: \`${app.appUrl}\`
- **Default Scopes**: \`openid profile email\`

### Environment Variables (\`.env\`)
\`\`\`env
PORTAL_URL=${issuerUrl}
PORTAL_CLIENT_ID=${clientId}
PORTAL_REDIRECT_URI=${redirectUri}
PORTAL_SCOPES=openid profile email
\`\`\`

---

## 2. Package Installation

Install the official client SDK:

\`\`\`bash
# Official NPM registry:
npm install @tanmaysinghx/portal-sso

# Or direct from GitHub repository:
npm install github:tanmaysinghx/portal-sso#path:packages/portal-sso
\`\`\`

---

## 3. Implementation Code

### Option A: Drop-in UI Button (Any Framework / HTML)
In your entry file (\`main.ts\` or \`index.tsx\`):
\`\`\`ts
import '@tanmaysinghx/portal-sso';
\`\`\`

In your template:
\`\`\`html
<portal-login-button
  portal-url="${issuerUrl}"
  client-id="${clientId}"
  theme="dark">
</portal-login-button>

<script>
  document.querySelector('portal-login-button').addEventListener('login-success', (e) => {
    const { user, session } = e.detail;
    console.log('Logged in user:', user.name, user.email);
  });
</script>
\`\`\`

### Option B: React / Next.js
\`\`\`tsx
import { PortalSSO } from '@tanmaysinghx/portal-sso';

export const sso = new PortalSSO({
  portalUrl: '${issuerUrl}',
  clientId: '${clientId}',
});

// Inside your login component:
async function handleLogin() {
  const { user, accessToken } = await sso.loginWithPopup();
  console.log('Signed in:', user.name, user.email);
}
\`\`\`

### Option C: Angular (Standalone & Signals)
\`\`\`typescript
import { PortalSSO } from '@tanmaysinghx/portal-sso';

const sso = new PortalSSO({
  portalUrl: '${issuerUrl}',
  clientId: '${clientId}',
});

async function login() {
  const session = await sso.loginWithPopup();
  console.log('User:', session.user);
}
\`\`\`
`;

    const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `PORTAL_SSO_${app.name.toUpperCase().replace(/[^A-Z0-9]/g, '_')}_INSTRUCTIONS.md`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    this.snackbarService.success(`Downloaded AI Agent instructions for "${app.name}".`);
  }

  downloadAgentInstructionsFromSummary(): void {
    const summary = this.createdAppSummary();
    if (!summary) return;
    const issuerUrl = summary.issuerUrl || (typeof window !== 'undefined' ? window.location.origin : 'https://portal.tanmaysinghx.com');
    const clientId = summary.clientId || 'your-client-id';
    const redirectUri = `${issuerUrl}/oauth2/popup-callback`;

    const content = `# Portal SSO Integration Instructions for ${summary.appName}

> Give this file directly to an AI agent (Cursor, Claude Code, GitHub Copilot, ChatGPT, or Antigravity):
> **"Integrate Portal SSO authentication into this repository following the instructions below."**

---

## 1. Credentials & Configuration

- **Portal URL**: \`${issuerUrl}\`
- **Client ID**: \`${clientId}\`
- **Popup Callback URI**: \`${redirectUri}\`
- **Application URL**: \`${summary.appUrl}\`
- **Default Scopes**: \`${summary.scopes?.join(' ') || 'openid profile email'}\`

### Environment Variables (\`.env\`)
\`\`\`env
${summary.envSnippet}
\`\`\`

---

## 2. Package Installation

Install the official client SDK:

\`\`\`bash
# Official NPM registry:
npm install @tanmaysinghx/portal-sso

# Or direct from GitHub repository:
npm install github:tanmaysinghx/portal-sso#path:packages/portal-sso
\`\`\`

---

## 3. Implementation Code

### Option A: Drop-in UI Button (Any Framework / HTML)
In your entry file (\`main.ts\` or \`index.tsx\`):
\`\`\`ts
import '@tanmaysinghx/portal-sso';
\`\`\`

In your template:
\`\`\`html
<portal-login-button
  portal-url="${issuerUrl}"
  client-id="${clientId}"
  theme="dark">
</portal-login-button>

<script>
  document.querySelector('portal-login-button').addEventListener('login-success', (e) => {
    const { user, session } = e.detail;
    console.log('Logged in user:', user.name, user.email);
  });
</script>
\`\`\`

### Option B: React / Next.js
\`\`\`tsx
import { PortalSSO } from '@tanmaysinghx/portal-sso';

export const sso = new PortalSSO({
  portalUrl: '${issuerUrl}',
  clientId: '${clientId}',
});

// Inside your login component:
async function handleLogin() {
  const { user, accessToken } = await sso.loginWithPopup();
  console.log('Signed in:', user.name, user.email);
}
\`\`\`

### Option C: Angular (Standalone & Signals)
\`\`\`typescript
import { PortalSSO } from '@tanmaysinghx/portal-sso';

const sso = new PortalSSO({
  portalUrl: '${issuerUrl}',
  clientId: '${clientId}',
});

async function login() {
  const session = await sso.loginWithPopup();
  console.log('User:', session.user);
}
\`\`\`
`;

    const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `PORTAL_SSO_${summary.appName.toUpperCase().replace(/[^A-Z0-9]/g, '_')}_INSTRUCTIONS.md`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    this.snackbarService.success(`Downloaded AI Agent instructions for "${summary.appName}".`);
  }

  readonly categoryPresets = CATEGORY_PRESETS;

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    description: [''],
    appUrl: ['', [Validators.required, Validators.pattern(/^https?:\/\/.+/)]],
    iconUrl: [''],
    category: ['General', [Validators.required]],
    clientId: [''],
    createOAuthClient: [false],
    newClientId: [''],
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

  // Track selected role IDs in restricted mode
  readonly selectedRoleIds = signal<string[]>([]);

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
        stat === 'all' ||
        (stat === 'enabled' && app.enabled) ||
        (stat === 'disabled' && !app.enabled);

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

    this.form.controls.name.valueChanges.subscribe((name) => {
      if (this.modalMode() === 'create' && this.form.controls.createOAuthClient.value) {
        if (!this.form.controls.newClientId.dirty || !this.form.controls.newClientId.value) {
          this.form.controls.newClientId.setValue(this.toClientId(name || ''), { emitEvent: false });
        }
      }
    });

    this.form.controls.appUrl.valueChanges.subscribe((url) => {
      if (this.modalMode() === 'create' && this.form.controls.createOAuthClient.value) {
        if (!this.form.controls.redirectUris.dirty || !this.form.controls.redirectUris.value) {
          const trimmed = (url || '').trim();
          if (trimmed) {
            this.form.controls.redirectUris.setValue(`${trimmed}/auth/callback\n${trimmed}`, { emitEvent: false });
          }
        }
      }
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.pendingDelete()) {
      this.pendingDelete.set(null);
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

    this.oauthClientService.list('', null, 0, 100).subscribe({
      next: (page) => this.availableOAuthClients.set(page.content),
      error: () => {},
    });
  }

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
      clientId: '',
      createOAuthClient: true,
      newClientId: '',
      redirectUris: '',
      profileScope: true,
      emailScope: true,
      confidential: false,
      externalAuthEnabled: true,
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
    this.form.patchValue({
      name: app.name,
      description: app.description ?? '',
      appUrl: app.appUrl,
      iconUrl: app.iconUrl ?? '',
      category: app.category,
      clientId: app.clientId ?? '',
      createOAuthClient: false,
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

  useLaunchUrlAsRedirectUri(): void {
    const appUrl = this.form.controls.appUrl.value.trim();
    if (appUrl) {
      this.form.controls.redirectUris.setValue(appUrl);
    }
  }

  copyIssuedSecret(): void {
    const secret = this.issuedClientSecret();
    if (!secret) {
      return;
    }
    navigator.clipboard.writeText(secret).then(
      () => {
        this.secretCopied.set(true);
        setTimeout(() => this.secretCopied.set(false), 2000);
      },
      () => this.secretCopied.set(false),
    );
  }

  clearIssuedSecret(): void {
    this.issuedClientId.set(null);
    this.issuedClientSecret.set(null);
    this.secretCopied.set(false);
  }

  copyIssuedAppApiKey(): void {
    const key = this.issuedAppApiKey()?.apiKey;
    if (!key) {
      return;
    }
    navigator.clipboard.writeText(key).then(
      () => {
        this.appApiKeyCopied.set(true);
        setTimeout(() => this.appApiKeyCopied.set(false), 2000);
      },
      () => this.appApiKeyCopied.set(false),
    );
  }

  clearIssuedAppApiKey(): void {
    this.issuedAppApiKey.set(null);
    this.appApiKeyCopied.set(false);
  }

  rotateExternalAuthApiKey(app: Application): void {
    this.applicationService.rotateExternalAuthApiKey(app.id).subscribe({
      next: (response) => {
        this.issuedAppApiKey.set(response);
        this.applications.set(
          this.applications().map((a) =>
            a.id === app.id
              ? {
                  ...a,
                  externalAuthEnabled: true,
                  externalAuthApiKeyIssued: true,
                  externalAuthApiKeyPrefix: response.apiKeyPrefix,
                }
              : a,
          ),
        );
        this.snackbarService.success(`External API key generated for "${app.name}".`);
      },
      error: (err) => {
        this.snackbarService.error(err?.error?.message ?? 'Failed to generate external API key.');
      },
    });
  }

  toggleRoleSelection(roleId: string): void {
    const current = this.selectedRoleIds();
    if (current.includes(roleId)) {
      this.selectedRoleIds.set(current.filter((id) => id !== roleId));
    } else {
      this.selectedRoleIds.set([...current, roleId]);
    }
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
      this.modalError.set('Please select at least one role for restricted access.');
      return;
    }

    this.submitting.set(true);
    this.modalError.set(null);

    if (this.modalMode() === 'create') {
      const createLinkedClient = val.createOAuthClient;
      const clientId = createLinkedClient
        ? (val.newClientId.trim() || this.toClientId(val.name))
        : val.clientId.trim();
      if (createLinkedClient && !clientId) {
        this.submitting.set(false);
        this.modalError.set('Enter a Client ID using letters, numbers, dots, underscores, or hyphens.');
        return;
      }

      const issuerUrl = typeof window !== 'undefined' ? window.location.origin : 'https://portal.tanmaysinghx.com';
      let parsedRedirects = this.parseRedirectUris(val.redirectUris);
      if (parsedRedirects.length === 0 && val.appUrl) {
        const trimmed = val.appUrl.trim();
        parsedRedirects = [`${trimmed}/auth/callback`, trimmed];
      }
      const popupCallbackUri = `${issuerUrl}/oauth2/popup-callback`;
      if (!parsedRedirects.includes(popupCallbackUri)) {
        parsedRedirects.push(popupCallbackUri);
      }

      const payload: CreateApplicationRequest = {
        name: val.name.trim(),
        description: val.description.trim() || undefined,
        appUrl: val.appUrl.trim(),
        iconUrl: val.iconUrl.trim() || undefined,
        category: val.category.trim() || 'General',
        clientId: clientId || undefined,
        accessType,
        roleIds,
        enabled: val.enabled,
        displayOrder: val.displayOrder,
        externalAuthEnabled: val.externalAuthEnabled,
        externalRegistrationEnabled: val.externalRegistrationEnabled,
      };

      const createApplication$ = createLinkedClient
        ? this.oauthClientService
            .create({
              clientId,
              clientName: val.name.trim(),
              redirectUris: parsedRedirects,
              scopes: [
                'openid',
                ...(val.profileScope ? ['profile'] : []),
                ...(val.emailScope ? ['email'] : []),
              ],
              logoUrl: val.iconUrl.trim() || undefined,
              confidential: val.confidential,
            })
            .pipe(
              switchMap((createdClient) => {
                this.availableOAuthClients.set([createdClient.client, ...this.availableOAuthClients()]);
                if (createdClient.clientSecret) {
                  this.issuedClientId.set(createdClient.client.clientId);
                  this.issuedClientSecret.set(createdClient.clientSecret);
                }
                return this.applicationService.createApplication({
                  ...payload,
                  clientId: createdClient.client.clientId,
                });
              }),
            )
        : this.applicationService.createApplication(payload);

      createApplication$
        .pipe(
          switchMap((created) => {
            if (val.externalAuthEnabled) {
              return this.applicationService.rotateExternalAuthApiKey(created.id).pipe(
                map((keyResp) => ({
                  created: {
                    ...created,
                    externalAuthEnabled: true,
                    externalAuthApiKeyIssued: true,
                    externalAuthApiKeyPrefix: keyResp.apiKeyPrefix,
                  },
                  keyResp,
                })),
                catchError(() => of({ created, keyResp: null })),
              );
            }
            return of({ created, keyResp: null });
          }),
        )
        .subscribe({
          next: ({ created, keyResp }) => {
            this.applications.set([created, ...this.applications()]);
            this.submitting.set(false);
            this.closeModal();

            const envLines = [
              `# Portal SSO Configuration for ${created.name}`,
              `PORTAL_URL=${issuerUrl}`,
            ];
            if (created.clientId) {
              envLines.push(`PORTAL_CLIENT_ID=${created.clientId}`);
            }
            const secret = this.issuedClientSecret();
            if (secret) {
              envLines.push(`PORTAL_CLIENT_SECRET=${secret}`);
            }
            if (keyResp) {
              envLines.push(`PORTAL_API_KEY=${keyResp.apiKey}`);
            }
            if (createLinkedClient) {
              envLines.push(`PORTAL_SCOPES=openid${val.profileScope ? ' profile' : ''}${val.emailScope ? ' email' : ''}`);
              if (parsedRedirects.length > 0) {
                envLines.push(`PORTAL_REDIRECT_URI=${parsedRedirects[0]}`);
              }
            }

            this.createdAppSummary.set({
              appName: created.name,
              appUrl: created.appUrl,
              clientId: created.clientId || undefined,
              clientSecret: secret || undefined,
              externalApiKey: keyResp?.apiKey,
              headerName: keyResp?.headerName,
              registerEndpoint: keyResp?.registerEndpoint,
              loginEndpoint: keyResp?.loginEndpoint,
              issuerUrl,
              redirectUris: createLinkedClient ? parsedRedirects : undefined,
              scopes: createLinkedClient ? ['openid', ...(val.profileScope ? ['profile'] : []), ...(val.emailScope ? ['email'] : [])] : undefined,
              envSnippet: envLines.join('\n'),
              isNew: true,
            });

            const suffix = createLinkedClient ? ' and linked OAuth client' : '';
            this.snackbarService.success(`Application "${created.name}" created${suffix} successfully.`);
          },
          error: (err) => {
            this.submitting.set(false);
            this.modalError.set(err?.error?.message ?? 'Failed to create application.');
          },
        });
    } else {
      const current = this.editingApp();
      if (!current) return;

      const payload: UpdateApplicationRequest = {
        name: val.name.trim(),
        description: val.description.trim() || undefined,
        appUrl: val.appUrl.trim(),
        iconUrl: val.iconUrl.trim() || undefined,
        category: val.category.trim() || 'General',
        clientId: val.clientId.trim() || undefined,
        accessType,
        roleIds,
        enabled: val.enabled,
        displayOrder: val.displayOrder,
        externalAuthEnabled: val.externalAuthEnabled,
        externalRegistrationEnabled: val.externalRegistrationEnabled,
      };

      this.applicationService.updateApplication(current.id, payload).subscribe({
        next: (updated) => {
          this.applications.set(
            this.applications().map((a) => (a.id === updated.id ? updated : a)),
          );
          this.submitting.set(false);
          this.closeModal();
          this.snackbarService.success(`Application "${updated.name}" updated successfully.`);
        },
        error: (err) => {
          this.submitting.set(false);
          this.modalError.set(err?.error?.message ?? 'Failed to update application.');
        },
      });
    }
  }

  private parseRedirectUris(value: string): string[] {
    return value
      .split('\n')
      .map((uri) => uri.trim())
      .filter((uri) => uri.length > 0);
  }

  private toClientId(name: string): string {
    return name
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9._-]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 80);
  }

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
}
