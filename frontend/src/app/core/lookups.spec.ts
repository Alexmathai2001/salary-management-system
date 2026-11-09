import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PayStats } from './models';
import { countryName, Lookups, NO_OPTIONS } from './lookups';

const stat = (key: string): PayStats => ({ key, headcount: 1, totalCents: 0, avgCents: 0, medianCents: 0, p25Cents: 0, p75Cents: 0, minCents: 0, maxCents: 0 });

describe('Lookups', () => {
  let http: HttpTestingController;
  let lookups: Lookups;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    lookups = TestBed.inject(Lookups);
  });

  afterEach(() => http.verify());

  function answer(by: Record<string, string[]>): void {
    for (const req of http.match((r) => r.url.startsWith('/api/insights/by/'))) {
      req.flush((by[req.request.url.split('/').pop()!] ?? []).map(stat));
    }
  }

  const all = { country: ['US', 'IN', 'BR'], department: ['Sales'], jobLevel: ['L1', 'L2'], jobTitle: ['Engineer'] };

  it('builds option lists from the data, including inactive employees, with countries sorted by name', () => {
    let result: unknown;
    lookups.options$.subscribe((o) => (result = o));

    const requests = http.match((r) => r.url.startsWith('/api/insights/by/'));
    expect(requests).toHaveLength(4);
    expect(requests.every((r) => r.request.params.get('includeInactive') === 'true')).toBe(true);
    requests.forEach((r) => r.flush((all[r.request.url.split('/').pop() as keyof typeof all] ?? []).map(stat)));

    expect(result).toEqual({
      countries: ['BR', 'IN', 'US'], // Brazil, India, United States
      departments: ['Sales'],
      jobLevels: ['L1', 'L2'],
      jobTitles: ['Engineer'],
    });
  });

  it('fetches once however many pages ask', () => {
    lookups.options$.subscribe();
    answer(all);

    lookups.options$.subscribe();

    http.expectNone((r) => r.url.startsWith('/api/insights/by/'));
  });

  it('yields empty options instead of an error when the server fails', () => {
    let result: unknown;
    let failed = false;
    lookups.options$.subscribe({ next: (o) => (result = o), error: () => (failed = true) });

    for (const req of http.match((r) => r.url.startsWith('/api/insights/by/'))) {
      if (!req.cancelled) req.flush('down', { status: 500, statusText: 'Server Error' });
    }

    expect(failed).toBe(false);
    expect(result).toEqual(NO_OPTIONS);
  });

  it('does not cache a failure: the next page tries again', () => {
    lookups.options$.subscribe();
    for (const req of http.match((r) => r.url.startsWith('/api/insights/by/'))) {
      if (!req.cancelled) req.flush('down', { status: 500, statusText: 'Server Error' });
    }

    let retried: unknown;
    lookups.options$.subscribe((o) => (retried = o));
    answer(all);

    expect((retried as { countries: string[] }).countries).toEqual(['BR', 'IN', 'US']);
  });
});

describe('countryName', () => {
  it('turns ISO codes into names', () => {
    expect(countryName('IN')).toBe('India');
    expect(countryName('DE')).toBe('Germany');
  });

  it('falls back to the code for unknown or malformed values', () => {
    expect(countryName('ZZ')).toBe('Unknown Region');
    expect(countryName('not-a-code')).toBe('not-a-code');
  });
});
