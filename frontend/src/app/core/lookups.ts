import { Injectable, inject } from '@angular/core';
import { Observable, catchError, defer, forkJoin, map, of, shareReplay, tap } from 'rxjs';
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

export const NO_OPTIONS: LookupOptions = { countries: [], departments: [], jobLevels: [], jobTitles: [] };

/**
 * Values for filter dropdowns, derived from the data itself via the insights endpoints (inactive employees included),
 * so the UI never offers a department or country that does not exist and needs no hard-coded lists.
 *
 * Dropdowns are a convenience, so a failed lookup must never break the page that uses them: it yields empty options
 * instead of an error, and the failure is not cached, so the next page that asks tries again.
 */
@Injectable({ providedIn: 'root' })
export class Lookups {
  private readonly insights = inject(InsightsApi);
  private cached$: Observable<LookupOptions> | null = null;

  readonly options$: Observable<LookupOptions> = defer(() => this.load()).pipe(catchError(() => of(NO_OPTIONS)));

  private load(): Observable<LookupOptions> {
    this.cached$ ??= forkJoin({
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
      tap({ error: () => (this.cached$ = null) }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.cached$;
  }
}

function keys(stats: PayStats[]): string[] {
  return stats.map((s) => s.key);
}
