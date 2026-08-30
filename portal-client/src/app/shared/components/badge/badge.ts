import { Component, computed, input } from '@angular/core';

export type BadgeTone = 'success' | 'neutral' | 'danger' | 'brand';

const TONE_CLASSES: Record<BadgeTone, string> = {
  success: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  neutral: 'bg-ink-100 text-ink-600 ring-ink-900/10',
  danger: 'bg-red-50 text-red-700 ring-red-600/20',
  brand: 'bg-brand-50 text-brand-700 ring-brand-600/20',
};

@Component({
  selector: 'app-badge',
  template: `
    <span class="inline-flex items-center rounded-md px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset {{ toneClass() }}">
      <ng-content />
    </span>
  `,
})
export class Badge {
  readonly tone = input<BadgeTone>('neutral');
  protected readonly toneClass = computed(() => TONE_CLASSES[this.tone()]);
}
