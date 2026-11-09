import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { scan, switchMap } from 'rxjs';
import { InsightsApi } from '../../core/insights.api';
import { countryName, Lookups } from '../../core/lookups';
import { InsightsFilter, OutlierParams, OutlierQuery, OutlierReport } from '../../core/models';
import { formatMinor, formatUsdCents } from '../../core/money';
import { initialView, loadAsEvents, reduceView } from '../../core/view-state';

const DEFAULTS: OutlierParams = { minGroupSize: 5, zThreshold: 3.5, minDeviationPct: 20 };

@Component({
  selector: 'app-outliers-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
  ],
  templateUrl: './outliers-page.html',
  styleUrl: './outliers-page.scss',
})
export class OutliersPage {
  private readonly api = inject(InsightsApi);

  protected readonly options = toSignal(inject(Lookups).options$, { initialValue: null });
  protected readonly limits = [50, 100, 200];

  protected readonly filter = signal<InsightsFilter>({});
  protected readonly direction = signal<'any' | 'high' | 'low'>('any');
  protected readonly limit = signal(50);
  protected readonly tuning = signal<OutlierParams>({ ...DEFAULTS });

  protected readonly minGroupSize = new FormControl(DEFAULTS.minGroupSize, { nonNullable: true });
  protected readonly zThreshold = new FormControl(DEFAULTS.zThreshold, { nonNullable: true });
  protected readonly minDeviationPct = new FormControl(DEFAULTS.minDeviationPct, { nonNullable: true });

  protected readonly state = toSignal(
    toObservable(
      computed<OutlierQuery>(() => ({
        ...this.filter(),
        ...this.tuning(),
        direction: this.direction(),
        limit: this.limit(),
      })),
    ).pipe(
      switchMap((query) => loadAsEvents(this.api.outliers(query))),
      scan(reduceView<OutlierReport>, initialView<OutlierReport>()),
    ),
    { initialValue: initialView<OutlierReport>() },
  );

  protected readonly report = computed(() => this.state().data);
  protected readonly isDefault = computed(() => {
    const t = this.tuning();
    return t.minGroupSize === DEFAULTS.minGroupSize && t.zThreshold === DEFAULTS.zThreshold && t.minDeviationPct === DEFAULTS.minDeviationPct;
  });

  /** Set when the tuning inputs are out of range; the server enforces the same bounds. */
  protected readonly tuningProblem = signal('');

  protected readonly countryName = countryName;
  protected readonly formatMinor = formatMinor;
  protected readonly formatUsdCents = formatUsdCents;

  protected setFilter(key: 'country' | 'department', value: string): void {
    this.filter.update((f) => ({ ...f, [key]: value || undefined }));
  }

  protected applyTuning(): void {
    const next: OutlierParams = {
      minGroupSize: Number(this.minGroupSize.value),
      zThreshold: Number(this.zThreshold.value),
      minDeviationPct: Number(this.minDeviationPct.value),
    };
    const problem = validateTuning(next);
    this.tuningProblem.set(problem);
    if (!problem) {
      this.tuning.set(next);
    }
  }

  protected resetTuning(): void {
    this.minGroupSize.setValue(DEFAULTS.minGroupSize);
    this.zThreshold.setValue(DEFAULTS.zThreshold);
    this.minDeviationPct.setValue(DEFAULTS.minDeviationPct);
    this.tuningProblem.set('');
    this.tuning.set({ ...DEFAULTS });
  }

  protected retry(): void {
    this.tuning.update((t) => ({ ...t }));
  }

  protected sign(pct: number): string {
    return pct > 0 ? `+${pct}%` : `${pct}%`;
  }

  protected coveragePct(r: OutlierReport): number {
    const total = r.employeesEvaluated + r.employeesNotEvaluated;
    return total === 0 ? 0 : Math.round((r.employeesEvaluated / total) * 100);
  }
}

/** Mirrors the server's bounds so the person sees the problem before a round trip. Returns '' when valid. */
export function validateTuning(p: OutlierParams): string {
  if (!Number.isInteger(p.minGroupSize) || p.minGroupSize < 3 || p.minGroupSize > 100) {
    return 'Minimum group size must be a whole number from 3 to 100.';
  }
  if (!(p.zThreshold >= 1 && p.zThreshold <= 10)) {
    return 'Statistical threshold must be between 1 and 10.';
  }
  if (!(p.minDeviationPct >= 0 && p.minDeviationPct <= 200)) {
    return 'Minimum gap must be between 0% and 200%.';
  }
  return '';
}
