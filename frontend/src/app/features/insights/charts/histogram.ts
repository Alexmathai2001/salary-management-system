import { Component, computed, input, signal } from '@angular/core';
import { niceScale } from '../../../core/chart-scale';
import { DistributionBucket } from '../../../core/models';

/**
 * A histogram for ONE series. Columns are capped at 24px with a 2px surface gap between neighbours; x labels sit on the
 * bin boundaries (a bin is a range, so labelling its centre or lower edge would misstate it); a tooltip appears on
 * hover and keyboard focus.
 */
@Component({
  selector: 'app-histogram',
  template: `
    <div class="chart" [attr.aria-label]="ariaLabel()" role="group">
      <div class="yaxis" aria-hidden="true">
        @for (t of scale().ticks; track t) {
          <span class="ytick" [style.bottom.%]="(t / scale().max) * 100">{{ t.toLocaleString('en') }}</span>
        }
      </div>

      <div class="plot">
        <div class="gridwrap" aria-hidden="true">
          @for (t of scale().ticks; track t) {
            <span class="gridline" [style.bottom.%]="(t / scale().max) * 100"></span>
          }
        </div>

        <div class="cols">
          @for (b of buckets(); track b.fromUsd; let i = $index) {
            <div
              class="col"
              tabindex="0"
              [class.active]="active() === i"
              [attr.aria-label]="rangeLabel(b) + ': ' + b.count.toLocaleString('en') + ' employees'"
              (pointerenter)="active.set(i)"
              (pointerleave)="active.set(null)"
              (focus)="active.set(i)"
              (blur)="active.set(null)"
            >
              <span class="bar" [style.height.%]="(b.count / scale().max) * 100"></span>
            </div>
          }
        </div>

        @if (active() !== null && buckets()[active()!]; as b) {
          <span class="tooltip" role="tooltip" [style.left.%]="tipLeft()">
            <strong>{{ b.count.toLocaleString('en') }} employees</strong>
            <span class="line">{{ rangeLabel(b) }}</span>
          </span>
        }

        <div class="xaxis" aria-hidden="true">
          @for (l of boundaryLabels(); track l.pos) {
            <span class="xtick" [style.left.%]="l.pos">{{ l.text }}</span>
          }
        </div>
      </div>
    </div>
  `,
  styleUrl: './histogram.scss',
})
export class Histogram {
  readonly buckets = input.required<DistributionBucket[]>();
  readonly ariaLabel = input.required<string>();
  /** Formats a USD amount for axis labels and the tooltip range, e.g. 40000 -> "$40K". */
  readonly formatUsd = input.required<(usd: number) => string>();

  protected readonly active = signal<number | null>(null);
  protected readonly scale = computed(() => niceScale(Math.max(0, ...this.buckets().map((b) => b.count)), 4));

  /** About eight labels along the bottom, on the bin boundaries, whatever the bin count. */
  protected readonly boundaryLabels = computed(() => {
    const b = this.buckets();
    if (b.length === 0) return [];
    const every = Math.max(1, Math.ceil(b.length / 8));
    const bounds = [...b.map((x) => x.fromUsd), b[b.length - 1].toUsd];
    return bounds
      .map((usd, i) => ({ usd, i }))
      .filter(({ i }) => i % every === 0)
      .map(({ usd, i }) => ({ pos: (i / b.length) * 100, text: this.formatUsd()(usd) }));
  });

  protected rangeLabel(b: DistributionBucket): string {
    return `${this.formatUsd()(b.fromUsd)} to ${this.formatUsd()(b.toUsd)}`;
  }

  protected tipLeft(): number {
    const i = this.active() ?? 0;
    const centre = ((i + 0.5) / Math.max(1, this.buckets().length)) * 100;
    return Math.min(Math.max(centre, 14), 86); // keep the tooltip inside the plot
  }
}
