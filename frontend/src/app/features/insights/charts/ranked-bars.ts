import { Component, computed, input, signal } from '@angular/core';
import { niceScale } from '../../../core/chart-scale';

export interface BarDatum {
  label: string;
  value: number;
  /** Extra tooltip lines (also the content of the table view elsewhere, so nothing is hover-only). */
  detail: string[];
}

/**
 * Ranked horizontal bars for ONE series: one colour for every bar (the value is already encoded by length), the value
 * printed at the bar tip, hairline recessive grid, and a tooltip on hover and keyboard focus.
 */
@Component({
  selector: 'app-ranked-bars',
  template: `
    <div class="chart" role="list" [attr.aria-label]="ariaLabel()">
      <div class="gridwrap" aria-hidden="true">
        @for (t of scale().ticks; track t) {
          <span class="gridline" [style.left.%]="pct(t)"></span>
        }
      </div>

      @for (d of data(); track d.label; let i = $index) {
        <div
          class="row"
          role="listitem"
          tabindex="0"
          [class.active]="active() === i"
          [attr.aria-label]="d.label + ': ' + formatValue()(d.value) + '. ' + d.detail.join('. ')"
          (pointerenter)="active.set(i)"
          (pointerleave)="active.set(null)"
          (focus)="active.set(i)"
          (blur)="active.set(null)"
        >
          <span class="label" [title]="d.label">{{ d.label }}</span>
          <span class="track">
            <span class="bar" [style.width.%]="pct(d.value)"></span>
            <span class="value" [style.left]="'calc(' + pct(d.value) + '% + 8px)'">{{ formatValue()(d.value) }}</span>
            @if (active() === i) {
              <span class="tooltip" role="tooltip" [style.left.%]="tipLeft(d.value)">
                <strong>{{ formatValue()(d.value) }}</strong>
                <span class="name">{{ d.label }}</span>
                @for (line of d.detail; track line) {
                  <span class="line">{{ line }}</span>
                }
              </span>
            }
          </span>
        </div>
      }

      <div class="axis" aria-hidden="true">
        @for (t of scale().ticks; track t) {
          <span class="tick" [style.left.%]="pct(t)">{{ formatTick()(t) }}</span>
        }
      </div>
    </div>
  `,
  styleUrl: './ranked-bars.scss',
})
export class RankedBars {
  readonly data = input.required<BarDatum[]>();
  readonly ariaLabel = input.required<string>();
  readonly formatValue = input.required<(value: number) => string>();
  readonly formatTick = input<(value: number) => string>((v) => String(v));

  protected readonly active = signal<number | null>(null);
  protected readonly scale = computed(() => niceScale(Math.max(0, ...this.data().map((d) => d.value))));

  protected pct(value: number): number {
    return (value / this.scale().max) * 100;
  }

  /** Keeps the tooltip inside the plot when the bar is long. */
  protected tipLeft(value: number): number {
    return Math.min(this.pct(value), 55);
  }
}
