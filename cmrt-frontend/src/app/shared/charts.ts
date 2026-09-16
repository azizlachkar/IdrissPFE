import { CommonModule } from '@angular/common';
import { Component, Input, computed, signal } from '@angular/core';
import { CHART_COLORS } from '../core/labels';
import { Metric } from '../core/models';
import { LabelPipe } from './pipes';

/**
 * Dependency-free SVG charts.
 *
 * The dashboards need three shapes only - a share-of-total ring, ranked bars and a
 * trend line - so they are drawn directly rather than pulling in a charting library.
 * All three take the same {@link Metric} list the API already returns.
 */

interface Slice {
  metric: Metric;
  color: string;
  dash: string;
  offset: number;
  percent: number;
}

@Component({
  selector: 'app-donut-chart',
  standalone: true,
  imports: [CommonModule, LabelPipe],
  template: `
    <div class="donut-wrap" *ngIf="total() > 0; else empty">
      <svg viewBox="0 0 42 42" class="donut" role="img" [attr.aria-label]="ariaLabel">
        <circle cx="21" cy="21" r="15.9155" fill="transparent" stroke="var(--border)" stroke-width="4.5"></circle>
        <circle
          *ngFor="let slice of slices()"
          cx="21" cy="21" r="15.9155"
          fill="transparent"
          [attr.stroke]="slice.color"
          stroke-width="4.5"
          [attr.stroke-dasharray]="slice.dash"
          [attr.stroke-dashoffset]="slice.offset"
          transform="rotate(-90 21 21)">
          <title>{{ slice.metric.label | label }} : {{ slice.metric.value }}</title>
        </circle>
        <text x="21" y="20.4" text-anchor="middle" class="donut-total">{{ total() }}</text>
        <text x="21" y="24.6" text-anchor="middle" class="donut-cap">{{ caption }}</text>
      </svg>

      <ul class="legend">
        <li *ngFor="let slice of slices()">
          <span class="swatch" [style.background]="slice.color"></span>
          <span class="name">{{ slice.metric.label | label }}</span>
          <span class="val">{{ slice.metric.value }}</span>
          <span class="pct">{{ slice.percent }}%</span>
        </li>
      </ul>
    </div>

    <ng-template #empty>
      <div class="empty"><span class="icon">◔</span><p>Aucune donnée</p></div>
    </ng-template>
  `,
  styles: [`
    .donut-wrap { display: flex; align-items: center; gap: 1.2rem; flex-wrap: wrap; }
    .donut { width: 152px; height: 152px; flex-shrink: 0; }
    .donut circle { transition: stroke-dasharray .5s ease; }
    .donut-total { font-size: 7px; font-weight: 800; fill: var(--text); }
    .donut-cap { font-size: 2.6px; fill: var(--text-muted); text-transform: uppercase; letter-spacing: .12em; font-weight: 700; }
    .legend { list-style: none; flex: 1; min-width: 175px; display: flex; flex-direction: column; gap: .3rem; }
    .legend li { display: flex; align-items: center; gap: .5rem; font-size: .78rem; }
    .swatch { width: 9px; height: 9px; border-radius: 2px; flex-shrink: 0; }
    .name { flex: 1; color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .val { font-weight: 700; font-variant-numeric: tabular-nums; }
    .pct { color: var(--text-muted); font-size: .71rem; min-width: 34px; text-align: right; font-variant-numeric: tabular-nums; }
  `]
})
export class DonutChartComponent {
  @Input({ required: true }) set data(value: Metric[]) {
    this.metrics.set((value ?? []).filter(m => m.value > 0));
  }
  @Input() caption = 'total';
  @Input() ariaLabel = 'Répartition';

  protected metrics = signal<Metric[]>([]);
  protected total = computed(() => this.metrics().reduce((sum, m) => sum + m.value, 0));

  protected slices = computed<Slice[]>(() => {
    const total = this.total();
    if (total === 0) return [];

    // The circumference is normalised to 100, so a dasharray reads as a percentage.
    let consumed = 0;
    return this.metrics().map((metric, index) => {
      const percent = Math.round((metric.value / total) * 1000) / 10;
      const slice: Slice = {
        metric,
        color: CHART_COLORS[index % CHART_COLORS.length],
        dash: `${percent} ${100 - percent}`,
        offset: -consumed,
        percent: Math.round(percent)
      };
      consumed += percent;
      return slice;
    });
  });
}

@Component({
  selector: 'app-bar-chart',
  standalone: true,
  imports: [CommonModule, LabelPipe],
  template: `
    <div class="bars" *ngIf="rows().length; else empty">
      <div class="bar-row" *ngFor="let row of rows(); let i = index">
        <span class="bar-label" [title]="row.label">{{ row.label | label }}</span>
        <div class="bar-track">
          <span class="bar-fill"
                [style.width.%]="row.pct"
                [style.background]="color(i)"></span>
        </div>
        <span class="bar-value">{{ row.value }}</span>
      </div>
    </div>

    <ng-template #empty>
      <div class="empty"><span class="icon">▤</span><p>Aucune donnée</p></div>
    </ng-template>
  `,
  styles: [`
    .bars { display: flex; flex-direction: column; gap: .55rem; }
    .bar-row { display: grid; grid-template-columns: minmax(90px, 34%) 1fr auto; align-items: center; gap: .6rem; }
    .bar-label { font-size: .76rem; color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .bar-track { height: 9px; background: var(--surface-alt); border-radius: 999px; overflow: hidden; }
    .bar-fill { display: block; height: 100%; border-radius: 999px; min-width: 3px; transition: width .5s ease; }
    .bar-value { font-size: .78rem; font-weight: 700; font-variant-numeric: tabular-nums; min-width: 24px; text-align: right; }
  `]
})
export class BarChartComponent {
  @Input({ required: true }) set data(value: Metric[]) {
    this.metrics.set(value ?? []);
  }
  /** Draw every series in one colour when the categories aren't independent. */
  @Input() monochrome = false;

  protected metrics = signal<Metric[]>([]);

  protected rows = computed(() => {
    const items = this.metrics();
    const max = Math.max(1, ...items.map(m => m.value));
    return items.map(m => ({
      label: m.label,
      value: m.value,
      pct: (m.value / max) * 100
    }));
  });

  protected color(index: number): string {
    return this.monochrome ? CHART_COLORS[0] : CHART_COLORS[index % CHART_COLORS.length];
  }
}

@Component({
  selector: 'app-line-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    <svg *ngIf="points().length > 1; else empty"
         [attr.viewBox]="'0 0 ' + width + ' ' + height"
         class="line-chart" preserveAspectRatio="none" role="img" [attr.aria-label]="ariaLabel">
      <line *ngFor="let y of gridLines()"
            [attr.x1]="pad" [attr.x2]="width - pad" [attr.y1]="y" [attr.y2]="y"
            stroke="var(--border)" stroke-width="1" stroke-dasharray="3 3"></line>

      <polygon [attr.points]="areaPath()" fill="url(#lineFade)"></polygon>
      <polyline [attr.points]="linePath()" fill="none" stroke="var(--chart-1)" stroke-width="2.2"
                stroke-linejoin="round" stroke-linecap="round"></polyline>

      <g *ngFor="let p of points()">
        <circle [attr.cx]="p.x" [attr.cy]="p.y" r="3.5" fill="var(--surface)" stroke="var(--chart-1)" stroke-width="2">
          <title>{{ p.label }} : {{ p.value }}</title>
        </circle>
      </g>

      <defs>
        <linearGradient id="lineFade" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="var(--chart-1)" stop-opacity=".22"></stop>
          <stop offset="100%" stop-color="var(--chart-1)" stop-opacity="0"></stop>
        </linearGradient>
      </defs>
    </svg>

    <div class="x-axis" *ngIf="points().length > 1">
      <span *ngFor="let p of points()">{{ p.label }}</span>
    </div>

    <ng-template #empty>
      <div class="empty"><span class="icon">📈</span><p>Pas assez de données</p></div>
    </ng-template>
  `,
  styles: [`
    .line-chart { width: 100%; height: 168px; display: block; }
    .x-axis { display: flex; justify-content: space-between; padding: .3rem .2rem 0; }
    .x-axis span { font-size: .68rem; color: var(--text-muted); font-variant-numeric: tabular-nums; }
  `]
})
export class LineChartComponent {
  @Input({ required: true }) set data(value: Metric[]) {
    this.metrics.set(value ?? []);
  }
  @Input() ariaLabel = 'Évolution';

  protected readonly width = 400;
  protected readonly height = 160;
  protected readonly pad = 8;

  protected metrics = signal<Metric[]>([]);

  protected points = computed(() => {
    const items = this.metrics();
    if (items.length < 2) return [];

    // Always keep a non-zero span so a flat series still renders on the baseline.
    const max = Math.max(1, ...items.map(m => m.value));
    const usableW = this.width - this.pad * 2;
    const usableH = this.height - this.pad * 2;

    return items.map((m, i) => ({
      x: this.pad + (usableW * i) / (items.length - 1),
      y: this.pad + usableH - (m.value / max) * usableH,
      value: m.value,
      label: m.label
    }));
  });

  protected linePath = computed(() =>
    this.points().map(p => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ')
  );

  protected areaPath = computed(() => {
    const pts = this.points();
    if (!pts.length) return '';
    const baseline = this.height - this.pad;
    return `${pts[0].x},${baseline} ${this.linePath()} ${pts[pts.length - 1].x},${baseline}`;
  });

  protected gridLines = computed(() => {
    const usableH = this.height - this.pad * 2;
    return [0, 0.25, 0.5, 0.75, 1].map(f => this.pad + usableH * f);
  });
}
