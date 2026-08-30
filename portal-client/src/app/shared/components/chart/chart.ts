import {
  Component,
  ElementRef,
  OnDestroy,
  effect,
  inject,
  input,
  viewChild,
} from '@angular/core';
import type { EChartsOption } from 'echarts';

/**
 * Thin wrapper around ECharts so panels only ever describe an option object.
 *
 * <p>ECharts is imported dynamically: it is by far the heaviest dependency in the app, and pulling
 * it in statically would land it in the dashboard chunk even for the parts of the page that render
 * before any chart does. It also needs an explicit resize observer — ECharts sizes to its container
 * once and never notices layout changes on its own, which is the usual cause of a chart that looks
 * right until the window moves.
 */
@Component({
  selector: 'app-chart',
  template: `<div #host class="h-full w-full" [style.height.px]="height()"></div>`,
})
export class Chart implements OnDestroy {
  readonly option = input.required<EChartsOption>();
  readonly height = input<number>(280);
  /** Registered before the option is applied, for the choropleth. */
  readonly mapName = input<string | null>(null);
  readonly mapGeoJson = input<unknown | null>(null);

  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');
  private readonly elementRef = inject(ElementRef);

  private chart: import('echarts').ECharts | null = null;
  private resizeObserver: ResizeObserver | null = null;
  private disposed = false;

  constructor() {
    effect(() => {
      const option = this.option();
      const mapName = this.mapName();
      const geoJson = this.mapGeoJson();
      void this.render(option, mapName, geoJson);
    });
  }

  private async render(option: EChartsOption, mapName: string | null, geoJson: unknown | null): Promise<void> {
    const echarts = await import('echarts');
    if (this.disposed) {
      return;
    }

    if (mapName && geoJson) {
      // Registering the same name twice is harmless and keeps this idempotent across re-renders.
      echarts.registerMap(mapName, geoJson as never);
    }

    if (!this.chart) {
      this.chart = echarts.init(this.host().nativeElement, undefined, { renderer: 'canvas' });
      this.resizeObserver = new ResizeObserver(() => this.chart?.resize());
      this.resizeObserver.observe(this.elementRef.nativeElement);
    }

    // notMerge so a series that disappears between renders is actually removed rather than
    // lingering from the previous option.
    this.chart.setOption(option, { notMerge: true });
  }

  ngOnDestroy(): void {
    this.disposed = true;
    this.resizeObserver?.disconnect();
    this.chart?.dispose();
    this.chart = null;
  }
}
