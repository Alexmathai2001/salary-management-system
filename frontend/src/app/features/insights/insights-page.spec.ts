import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Earner, PayStats } from '../../core/models';
import { InsightsPage } from './insights-page';

function stats(key: string, medianDollars: number, headcount = 100): PayStats {
  const cents = medianDollars * 100;
  return {
    key,
    headcount,
    totalCents: cents * headcount,
    avgCents: cents + 100_000,
    medianCents: cents,
    p25Cents: cents - 500_000,
    p75Cents: cents + 700_000,
    minCents: cents - 2_000_000,
    maxCents: cents + 3_000_000,
  };
}

const earner = (id: number, name: string, usdCents: number): Earner => ({
  id, employeeCode: `EMP-${id}`, name, country: 'US', department: 'Engineering', jobTitle: 'Engineer', jobLevel: 'L3',
  currency: 'USD', salaryMinor: usdCents, usdCents,
});

describe('InsightsPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<InsightsPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InsightsPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InsightsPage);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const root = () => fixture.nativeElement as HTMLElement;
  const text = () => root().textContent ?? '';

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  /** Answers every insight request the page (and its dropdown lookups) made, with overridable fixtures. */
  function flushAll(over: { summary?: PayStats; groups?: PayStats[] } = {}): TestRequest[] {
    TestBed.tick();
    const requests = http.match((r) => r.url.startsWith('/api/insights'));
    for (const req of requests) {
      const url = req.request.url;
      const params = req.request.params;
      if (url.endsWith('/summary')) {
        req.flush(over.summary ?? stats('ALL', 54_891, 9_514));
      } else if (url.includes('/by/') && params.get('includeInactive') === 'true') {
        req.flush([stats('IN', 1), stats('US', 1)]); // dropdown options
      } else if (url.includes('/by/')) {
        req.flush(over.groups ?? [stats('IN', 25_081, 2_272), stats('US', 86_400, 1_924), stats('BR', 29_860, 496)]);
      } else if (url.endsWith('/distribution')) {
        req.flush([{ fromUsd: 0, toUsd: 20_000, count: 10 }, { fromUsd: 20_000, toUsd: 40_000, count: 40 }]);
      } else if (url.endsWith('/earners')) {
        req.flush(params.get('order') === 'top' ? [earner(1, 'Top Earner', 40_000_000)] : [earner(2, 'Low Earner', 2_000_000)]);
      }
    }
    return requests;
  }

  it('asks for all five datasets, scoped to active employees by default', () => {
    const urls = flushAll().filter((r) => r.request.params.get('includeInactive') === 'false').map((r) => r.request.url);

    expect(urls.sort()).toEqual([
      '/api/insights/by/country',
      '/api/insights/distribution',
      '/api/insights/earners',
      '/api/insights/earners',
      '/api/insights/summary',
    ]);
  });

  it('shows the headline figures', async () => {
    flushAll();
    await settle();

    expect(text()).toContain('9,514'); // employees
    expect(text()).toContain('$54,891'); // median
    expect(text()).toContain('Median annual pay by country');
  });

  it('ranks countries by median pay, highest first, using country names', async () => {
    flushAll();
    await settle();

    const labels = Array.from(root().querySelectorAll('app-ranked-bars .label')).map((l) => l.textContent);
    expect(labels).toEqual(['United States', 'Brazil', 'India']);
  });

  it('keeps levels in their natural order instead of ranking them by pay', async () => {
    flushAll();
    await settle();
    fixture.componentInstance['dimension'].set('jobLevel');
    flushAll({ groups: [stats('L2', 70_000), stats('L1', 40_000), stats('L3', 95_000)] });
    await settle();

    const labels = Array.from(root().querySelectorAll('app-ranked-bars .label')).map((l) => l.textContent);
    expect(labels).toEqual(['L1', 'L2', 'L3']);
  });

  it('refetches the grouping when the dimension changes', async () => {
    flushAll();
    await settle();

    fixture.componentInstance['dimension'].set('department');
    TestBed.tick();

    expect(http.expectOne((r) => r.url === '/api/insights/by/department' && r.params.get('includeInactive') === 'false'));
    http.match((r) => r.url.startsWith('/api/insights')).forEach((r) => r.flush([]));
  });

  it('scopes every dataset when a filter is chosen', async () => {
    flushAll();
    await settle();

    fixture.componentInstance['setFilter']('country', 'IN');
    TestBed.tick();

    const scoped = http.match((r) => r.url.startsWith('/api/insights'));
    expect(scoped).toHaveLength(5);
    expect(scoped.every((r) => r.request.params.get('country') === 'IN')).toBe(true);
    scoped.forEach((r) => r.flush([]));
  });

  it('keeps showing the previous charts, dimmed, while new data loads', async () => {
    flushAll();
    await settle();

    fixture.componentInstance['setIncludeInactive'](true);
    TestBed.tick();
    fixture.detectChanges();

    expect(root().querySelector('.content.stale')).not.toBeNull();
    expect(text()).toContain('$54,891'); // old figures still on screen
    http.match((r) => r.url.startsWith('/api/insights')).forEach((r) => r.flush([]));
  });

  it('switches to a table view with every statistic', async () => {
    flushAll();
    await settle();

    fixture.componentInstance['view'].set('table');
    await settle();

    const headers = Array.from(root().querySelectorAll('table.data')[0].querySelectorAll('th[scope="col"]')).map((h) => h.textContent);
    expect(headers).toContain('Median');
    expect(headers).toContain('Middle 50%');
    expect(root().querySelector('app-ranked-bars')).toBeNull();
    expect(root().querySelectorAll('table.data')[0].querySelectorAll('tbody tr')).toHaveLength(3);
  });

  it('lists the highest and lowest paid with links to their pages', async () => {
    flushAll();
    await settle();

    expect(text()).toContain('Highest paid');
    expect(text()).toContain('Top Earner');
    expect(text()).toContain('$400,000');
    expect(text()).toContain('Low Earner');
    expect(root().querySelector('a[href="/employees/1"]')).not.toBeNull();
  });

  it('explains an empty result instead of drawing empty charts', async () => {
    flushAll({ summary: { ...stats('ALL', 0, 0), headcount: 0 }, groups: [] });
    await settle();

    expect(text()).toContain('No employees match these filters');
    expect(root().querySelector('app-ranked-bars')).toBeNull();
  });

  it('shows the server message if loading fails', async () => {
    TestBed.tick();
    for (const req of http.match((r) => r.url.startsWith('/api/insights'))) {
      // forkJoin cancels its siblings after the first failure, and a cancelled request cannot be answered
      if (!req.cancelled) {
        req.flush({ detail: 'Database is locked' }, { status: 500, statusText: 'Server Error' });
      }
    }
    await settle();

    expect(text()).toContain('Could not load insights');
    expect(text()).toContain('Database is locked');
  });
});
