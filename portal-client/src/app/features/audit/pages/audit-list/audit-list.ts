import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { Subject, debounceTime } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SnackbarService } from '../../../../core/services/snackbar.service';
import { Badge, BadgeTone } from '../../../../shared/components/badge/badge';
import { AuditActionOption, AuditEvent } from '../../models/audit-event.model';
import { AuditService } from '../../services/audit.service';

const PAGE_SIZE = 25;

/**
 * Only actions that remove protection are coloured: an account disabled, a client deleted, a second
 * factor stripped. Everything else stays neutral.
 *
 * <p>Colouring creations too was the first instinct, but it put an orange badge beside a red one —
 * the pair an eye separates worst — for the single distinction this screen is scanned for. One
 * accent against a field of grey is what makes a weakened control findable. The tone is never the
 * only signal either way: the label always says what happened.
 */
const DESTRUCTIVE_ACTIONS = new Set(['USER_DISABLED', 'USER_MFA_RESET', 'CLIENT_DELETED']);

@Component({
  selector: 'app-audit-list',
  standalone: true,
  imports: [Badge, DatePipe],
  templateUrl: './audit-list.html',
})
export class AuditList {
  private readonly auditService = inject(AuditService);
  private readonly snackbarService = inject(SnackbarService);

  readonly events = signal<AuditEvent[]>([]);
  readonly actions = signal<AuditActionOption[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly exporting = signal(false);

  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  readonly actionFilter = signal('');
  readonly actorFilter = signal('');

  /** Which row is expanded to show details, user agent and target id. */
  readonly expandedId = signal<string | null>(null);

  readonly rangeLabel = computed(() => {
    const total = this.totalElements();
    if (total === 0) {
      return 'No entries';
    }
    const first = this.page() * PAGE_SIZE + 1;
    const last = Math.min(first + this.events().length - 1, total);
    return `${first}–${last} of ${total}`;
  });

  private readonly actorInput = new Subject<string>();
  private latestRequest = 0;

  constructor() {
    this.auditService.actions().subscribe({
      next: (actions) => this.actions.set(actions),
      // A missing filter list degrades the screen but does not break it, so this stays quiet.
      error: () => this.actions.set([]),
    });

    // Debounced so typing an address does not fire a query per keystroke. This subscription is the
    // only thing that applies an actor change, so a value pushed here supersedes any keystroke
    // still inside the window rather than racing it.
    this.actorInput.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((value) => {
      this.actorFilter.set(value);
      this.page.set(0);
      this.load();
    });

    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);

    // Responses are not guaranteed to arrive in request order, so a slow early query could
    // otherwise overwrite the results of a later one and leave the table disagreeing with the
    // filters shown above it. Only the newest request is allowed to write.
    const request = ++this.latestRequest;

    this.auditService
      .list({ action: this.actionFilter(), actor: this.actorFilter() }, this.page(), PAGE_SIZE)
      .subscribe({
        next: (result) => {
          if (request !== this.latestRequest) {
            return;
          }
          this.events.set(result.content);
          this.totalPages.set(result.totalPages);
          this.totalElements.set(result.totalElements);
          this.loading.set(false);
        },
        error: (err) => {
          if (request !== this.latestRequest) {
            return;
          }
          this.error.set(err.error?.message ?? 'Could not load the audit log.');
          this.loading.set(false);
        },
      });
  }

  onActionChange(value: string): void {
    this.actionFilter.set(value);
    this.page.set(0);
    this.load();
  }

  onActorInput(value: string): void {
    this.actorInput.next(value);
  }

  clearFilters(): void {
    this.actionFilter.set('');
    // Pushed through the debounced stream rather than loading directly: a keystroke still inside
    // the window would otherwise land after this and silently re-apply the filter just cleared.
    // The stream owns the reload, so Clear issues one request instead of two.
    this.actorInput.next('');
  }

  readonly hasFilters = computed(() => this.actionFilter() !== '' || this.actorFilter() !== '');

  goToPage(page: number): void {
    if (page < 0 || (this.totalPages() > 0 && page >= this.totalPages())) {
      return;
    }
    this.page.set(page);
    this.expandedId.set(null);
    this.load();
  }

  toggleExpanded(id: string): void {
    this.expandedId.update((current) => (current === id ? null : id));
  }

  toneFor(action: string): BadgeTone {
    return DESTRUCTIVE_ACTIONS.has(action) ? 'danger' : 'neutral';
  }

  exportCsv(): void {
    this.exporting.set(true);
    this.auditService
      .exportCsv({ action: this.actionFilter(), actor: this.actorFilter() })
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = `portal-sso-audit-${new Date().toISOString().slice(0, 10)}.csv`;
          link.click();
          URL.revokeObjectURL(url);
          this.exporting.set(false);
          this.snackbarService.success('Export Ready', 'The audit log has been downloaded as CSV.');
        },
        error: () => {
          this.exporting.set(false);
          this.snackbarService.error('Export Failed', 'Could not export the audit log.', 'PRTL-5000');
        },
      });
  }
}
