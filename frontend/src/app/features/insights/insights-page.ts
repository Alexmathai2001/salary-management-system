import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, scan, startWith, switchMap } from 'rxjs';
import { describeError } from '../../core/api-error';
import { InsightsApi } from '../../core/insights.api';
import { countryName, Lookups } from '../../core/lookups';
import { Dimension, DistributionBucket, Earner, InsightsFilter, PayStats } from '../../core/models';
import { formatUsdCents, formatUsdCompact } from '../../core/money';
import { BarDatum, RankedBars } from './charts/ranked-bars';
import { Histogram } from './charts/histogram';

interface InsightsData {
  summary: PayStats;
  groups: PayStats[];
  distribution: DistributionBucket[];
  top: Earner[];
  bottom: Earner[];
}

interface ViewState {
  data: InsightsData | null;
  loading: boolean;
  error: string;
}

type Event = { type: 'loading' } | { type: 'ok'; data: InsightsData } | { type: 'error'; message: string };

const INITIAL: ViewState = { data: null, loading: true, error: '' };

/** Keeps the last good data across loading and error events so the page never blanks while refetching. */
function reduce(prev: ViewState, e: Event): ViewState {
  switch (e.type) {
    case 'ok':
      return { data: e.data, loading: false, error: '' };
    case 'error':
      return { ...prev, loading: false, error: e.message };
    default:
      return { ...prev, loading: true, error: '' };
  }
}

const DIMENSIONS: { value: Dimension; label: string; noun: string }[] = [
  { value: 'country', label: 'Country', noun: 'country' },
  { value: 'department', label: 'Department', noun: 'department' },
  { value: 'jobLevel', label: 'Level', noun: 'level' },
  { value: 'jobTitle', label: 'Job title', noun: 'job title' },
];

@Component({
  selector: 'app-insights-page',
  imports: [
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatProgressBarModule,
    MatSelectModule,
    RankedBars,
    Histogram,
  ],
  templateUrl: './insights-page.html',
  styleUrl: './insights-page.scss',
})
export class InsightsPage {
  private readonly api = inject(InsightsApi);

  protected readonly options = toSignal(inject(Lookups).options$, { initialValue: null });
  protected readonly dimensions = DIMENSIONS;
  protected readonly bucketSizes = [10_000, 20_000, 25_000, 50_000];

  protected readonly filter = signal<InsightsFilter>({ includeInactive: false });
  protected readonly dimension = signal<Dimension>('country');
  protected readonly bucket = signal(20_000);
  protected readonly view = signal<'chart' | 'table'>('chart');

  /**
   * While a refetch is in flight the previous data stays on screen (dimmed), so changing a filter never blanks the page
   * or makes the layout jump.
   */
  protected readonly state = toSignal(
    toObservable(computed(() => ({ filter: this.filter(), dimension: this.dimension(), bucket: this.bucket() }))).pipe(
      switchMap(({ filter, dimension, bucket }) =>
        forkJoin({
          summary: this.api.summary(filter),
          groups: this.api.by(dimension, filter),
          distribution: this.api.distribution(bucket, filter),
          top: this.api.earners('top', 10, filter),
          bottom: this.api.earners('bottom', 10, filter),
        }).pipe(
          map((data): Event => ({ type: 'ok', data })),
          catchError((err) => of<Event>({ type: 'error', message: describeError(err).message })),
          startWith<Event>({ type: 'loading' }),
        ),
      ),
      scan(reduce, INITIAL),
    ),
    { initialValue: INITIAL },
  );

  protected readonly data = computed(() => this.state().data);
  protected readonly hasFilters = computed(() => {
    const f = this.filter();
    return !!(f.country || f.department || f.jobLevel || f.includeInactive);
  });

  protected readonly dimensionNoun = computed(() => DIMENSIONS.find((d) => d.value === this.dimension())!.noun);

  /** Median pay per group as chart bars, in whole USD. Levels keep their natural order; the rest rank high to low. */
  protected readonly bars = computed<BarDatum[]>(() => {
    const groups = this.data()?.groups ?? [];
    const dim = this.dimension();
    const items = groups.map((g) => ({
      label: this.groupLabel(g.key),
      value: Math.round(g.medianCents / 100),
      sortKey: g.key,
      detail: [
        `${g.headcount.toLocaleString('en')} employees`,
        `Middle 50%: ${formatUsdCents(g.p25Cents)} to ${formatUsdCents(g.p75Cents)}`,
        `Average: ${formatUsdCents(g.avgCents)}`,
        `Lowest to highest: ${formatUsdCents(g.minCents)} to ${formatUsdCents(g.maxCents)}`,
      ],
    }));
    items.sort((a, b) => (dim === 'jobLevel' ? a.sortKey.localeCompare(b.sortKey) : b.value - a.value));
    return items;
  });

  protected readonly usd = (dollars: number): string => formatUsdCents(dollars * 100);
  protected readonly usdCompact = (dollars: number): string => formatUsdCompact(dollars * 100);
  protected readonly formatUsdCents = formatUsdCents;
  protected readonly formatUsdCompact = formatUsdCompact;
  protected readonly countryName = countryName;

  protected groupLabel(key: string): string {
    return this.dimension() === 'country' ? countryName(key) : key;
  }

  protected setFilter(key: 'country' | 'department' | 'jobLevel', value: string): void {
    this.filter.update((f) => ({ ...f, [key]: value || undefined }));
  }

  protected setIncludeInactive(checked: boolean): void {
    this.filter.update((f) => ({ ...f, includeInactive: checked }));
  }

  protected clear(): void {
    this.filter.set({ includeInactive: false });
  }

  protected retry(): void {
    this.filter.update((f) => ({ ...f }));
  }
}
