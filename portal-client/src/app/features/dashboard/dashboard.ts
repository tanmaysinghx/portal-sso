import { DatePipe, LowerCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import type { EChartsOption } from 'echarts';
import { AuthService } from '../../core/services/auth.service';
import { Chart } from '../../shared/components/chart/chart';
import {
  DashboardStats,
  RANGE_OPTIONS,
  StatsRange,
} from './models/dashboard-stats.model';
import { DashboardStatsService } from './services/dashboard-stats.service';

/**
 * Palette roles, not raw hues.
 *
 * Every chart here is single-series, which is deliberate. The one pair that would naturally sit
 * together — successful vs failed sign-ins — measures ΔE 4.1 under deuteranopia as green/red, far
 * below the separation floor, so that distinction is carried by the table's icon + text label
 * instead of by colour anywhere. BRAND is the sequential hue for magnitude; INK is de-emphasis.
 */
const BRAND = '#ea580c';
const BRAND_SOFT = 'rgba(234, 88, 12, 0.16)';
const INK_GRID = 'rgba(113, 113, 122, 0.16)';
const INK_LABEL = '#71717a';
/** Choropleth ramp: one hue, light -> dark, so more logins reads as darker. */
const MAP_RAMP = ['#ffedd5', '#fdba74', '#fb923c', '#ea580c', '#9a3412'];

@Component({
  selector: 'app-dashboard',
  imports: [Chart, DatePipe, LowerCasePipe],
  templateUrl: './dashboard.html',
})
export class Dashboard {
  private readonly authService = inject(AuthService);
  private readonly statsService = inject(DashboardStatsService);

  readonly currentUser = this.authService.currentUser;
  readonly rangeOptions = RANGE_OPTIONS;

  readonly range = signal<StatsRange>('MONTH');
  readonly stats = signal<DashboardStats | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly exporting = signal(false);
  readonly worldGeoJson = signal<unknown | null>(null);

  constructor() {
    this.load();
  }

  setRange(range: StatsRange): void {
    if (range === this.range()) {
      return;
    }
    this.range.set(range);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.statsService.load(this.range()).subscribe({
      next: (stats) => {
        this.stats.set(stats);
        this.loading.set(false);
        // 400 KB of country geometry is only worth fetching once something can be drawn on it.
        if (stats.byCountry.some((c) => c.code) && !this.worldGeoJson()) {
          void this.loadWorldMap();
        }
      },
      error: () => {
        this.error.set('Could not load dashboard statistics.');
        this.loading.set(false);
      },
    });
  }

  private async loadWorldMap(): Promise<void> {
    try {
      const response = await fetch('world.geo.json');
      if (response.ok) {
        this.worldGeoJson.set(await response.json());
      }
    } catch {
      // The country table below the map still carries the data; the map is the enhancement.
    }
  }

  exportCsv(): void {
    this.exporting.set(true);
    this.statsService.exportCsv(this.range()).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `portal-sso-logins-${this.range().toLowerCase()}.csv`;
        link.click();
        URL.revokeObjectURL(url);
        this.exporting.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.exporting.set(false);
        this.error.set(err.status === 403 ? 'Not permitted to export.' : 'Could not export the CSV.');
      },
    });
  }

  /** Buckets arrive as yyyy-MM / yyyy-MM-dd / yyyy-MM-ddTHH:00; show only what varies. */
  private shortLabel(bucket: string): string {
    if (bucket.includes('T')) {
      return bucket.slice(11, 16);
    }
    const parts = bucket.split('-');
    if (parts.length === 3) {
      return `${parts[2]}/${parts[1]}`;
    }
    return bucket;
  }

  private axisBase(): EChartsOption {
    return {
      grid: { left: 8, right: 16, top: 16, bottom: 8, containLabel: true },
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#18181b',
        borderWidth: 0,
        textStyle: { color: '#fafafa', fontSize: 12 },
        axisPointer: { type: 'line', lineStyle: { color: INK_GRID } },
      },
    };
  }

  readonly signupsOption = computed<EChartsOption>(() => {
    const data = this.stats()?.signups ?? [];
    return {
      ...this.axisBase(),
      xAxis: {
        type: 'category',
        data: data.map((p) => this.shortLabel(p.bucket)),
        axisLine: { lineStyle: { color: INK_GRID } },
        axisTick: { show: false },
        axisLabel: { color: INK_LABEL, fontSize: 11 },
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        splitLine: { lineStyle: { color: INK_GRID } },
        axisLabel: { color: INK_LABEL, fontSize: 11 },
      },
      series: [
        {
          name: 'Users added',
          type: 'line',
          smooth: 0.3,
          showSymbol: false,
          lineStyle: { width: 2, color: BRAND },
          itemStyle: { color: BRAND },
          areaStyle: { color: BRAND_SOFT },
          data: data.map((p) => p.count),
        },
      ],
    };
  });

  readonly loginsOption = computed<EChartsOption>(() => {
    const data = this.stats()?.logins ?? [];
    return {
      ...this.axisBase(),
      xAxis: {
        type: 'category',
        data: data.map((p) => this.shortLabel(p.bucket)),
        axisLine: { lineStyle: { color: INK_GRID } },
        axisTick: { show: false },
        axisLabel: { color: INK_LABEL, fontSize: 11 },
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        splitLine: { lineStyle: { color: INK_GRID } },
        axisLabel: { color: INK_LABEL, fontSize: 11 },
      },
      series: [
        {
          name: 'Sign-ins',
          type: 'bar',
          // 4px rounded data-end anchored to the baseline.
          itemStyle: { color: BRAND, borderRadius: [4, 4, 0, 0] },
          barMaxWidth: 22,
          data: data.map((p) => p.successful),
        },
      ],
    };
  });

  readonly lastLoginOption = computed<EChartsOption>(() => {
    const data = this.stats()?.lastLoginBuckets ?? [];
    // Horizontal: the category labels are words, not dates, and read better along the y axis.
    return {
      grid: { left: 8, right: 32, top: 8, bottom: 8, containLabel: true },
      tooltip: { trigger: 'item', backgroundColor: '#18181b', borderWidth: 0, textStyle: { color: '#fafafa', fontSize: 12 } },
      xAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: INK_GRID } }, axisLabel: { color: INK_LABEL, fontSize: 11 } },
      yAxis: {
        type: 'category',
        data: data.map((d) => d.label).reverse(),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: INK_LABEL, fontSize: 11 },
      },
      series: [
        {
          type: 'bar',
          itemStyle: { color: BRAND, borderRadius: [0, 4, 4, 0] },
          barMaxWidth: 18,
          label: { show: true, position: 'right', color: INK_LABEL, fontSize: 11 },
          data: data.map((d) => d.count).reverse(),
        },
      ],
    };
  });

  readonly byClientOption = computed<EChartsOption>(() => {
    const data = this.stats()?.byClient ?? [];
    return {
      grid: { left: 8, right: 32, top: 8, bottom: 8, containLabel: true },
      tooltip: { trigger: 'item', backgroundColor: '#18181b', borderWidth: 0, textStyle: { color: '#fafafa', fontSize: 12 } },
      xAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: INK_GRID } }, axisLabel: { color: INK_LABEL, fontSize: 11 } },
      yAxis: {
        type: 'category',
        data: data.map((d) => d.clientName).reverse(),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: INK_LABEL, fontSize: 11, width: 120, overflow: 'truncate' },
      },
      series: [
        {
          type: 'bar',
          itemStyle: { color: BRAND, borderRadius: [0, 4, 4, 0] },
          barMaxWidth: 18,
          label: { show: true, position: 'right', color: INK_LABEL, fontSize: 11 },
          data: data.map((d) => d.logins).reverse(),
        },
      ],
    };
  });

  readonly mapOption = computed<EChartsOption>(() => {
    const countries = (this.stats()?.byCountry ?? []).filter((c) => c.code);
    const max = Math.max(1, ...countries.map((c) => c.logins));
    return {
      tooltip: {
        trigger: 'item',
        backgroundColor: '#18181b',
        borderWidth: 0,
        textStyle: { color: '#fafafa', fontSize: 12 },
        formatter: (params: unknown) => {
          const p = params as { name: string; value: number; data?: { label?: string } };
          const label = p.data?.label ?? p.name;
          return Number.isFinite(p.value) ? `${label}: ${p.value}` : `${label}: no sign-ins`;
        },
      },
      visualMap: {
        min: 0,
        max,
        // Horizontal along the bottom edge: a vertical bar on the left sat on top of Alaska and
        // Canada and hid its own "High" label.
        orient: 'horizontal',
        left: 'center',
        bottom: 0,
        itemWidth: 10,
        itemHeight: 70,
        text: ['High', 'Low'],
        calculable: false,
        inRange: { color: MAP_RAMP },
        textStyle: { color: INK_LABEL, fontSize: 11 },
      },
      series: [
        {
          type: 'map',
          map: 'world',
          roam: false,
          // Antarctica occupies a quarter of the frame and never carries data; nudging the centre
          // up keeps the populated latitudes in view.
          center: [10, 20],
          zoom: 1.2,
          itemStyle: { areaColor: '#f4f4f5', borderColor: '#e4e4e7', borderWidth: 0.5 },
          emphasis: { itemStyle: { areaColor: '#fdba74' }, label: { show: false } },
          data: countries.map((c) => ({ name: c.code as string, value: c.logins, label: c.name })),
        },
      ],
    } as EChartsOption;
  });

  readonly hasMappableCountries = computed(() => (this.stats()?.byCountry ?? []).some((c) => c.code));

  readonly successRate = computed(() => {
    const t = this.stats()?.totals;
    if (!t) return null;
    const total = t.logins + t.failedLogins;
    return total === 0 ? null : Math.round((t.logins / total) * 100);
  });

  /**
   * Whether a tile should draw the eye. Only the figures that imply an action — failed sign-ins,
   * locked accounts — and only once they are actually non-zero. A dashboard where a zero is
   * highlighted teaches people to ignore the highlight.
   */
  needsAttention(alerting: boolean | undefined, value: number | null | undefined): boolean {
    return !!alerting && !this.loading() && (value ?? 0) > 0;
  }
}
