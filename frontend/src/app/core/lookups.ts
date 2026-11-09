import { Injectable, inject } from '@angular/core';
import { forkJoin, map, shareReplay } from 'rxjs';
import { InsightsApi } from './insights.api';
import { PayStats } from './models';

export interface LookupOptions {
  countries: string[];
  departments: string[];
  jobLevels: string[];
  jobTitles: string[];
}

const regionNames = new Intl.DisplayNames(['en'], { type: 'region' });

/** "IN" -> "India". Falls back to the code if the runtime does not know it. */
export function countryName(code: string): string {
  try {
    return regionNames.of(code) ?? code;
  } catch {
    return code;
  }
}

/**
 * Values for filter dropdowns, derived from the data itself via the insights endpoints (inactive employees included),
 * so the UI never offers a department or country that does not exist and needs no hard-coded lists.
 */
@Injectable({ providedIn: 'root' })
export class Lookups {
  private readonly insights = inject(InsightsApi);

  readonly options$ = forkJoin({
    countries: this.insights.by('country', { includeInactive: true }),
    departments: this.insights.by('department', { includeInactive: true }),
    jobLevels: this.insights.by('jobLevel', { includeInactive: true }),
    jobTitles: this.insights.by('jobTitle', { includeInactive: true }),
  }).pipe(
    map(
      (r): LookupOptions => ({
        countries: keys(r.countries).sort((a, b) => countryName(a).localeCompare(countryName(b))),
        departments: keys(r.departments),
        jobLevels: keys(r.jobLevels),
        jobTitles: keys(r.jobTitles),
      }),
    ),
    shareReplay({ bufferSize: 1, refCount: false }),
  );
}

function keys(stats: PayStats[]): string[] {
  return stats.map((s) => s.key);
}
