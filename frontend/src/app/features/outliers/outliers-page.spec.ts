import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Outlier, OutlierReport } from '../../core/models';
import { OutliersPage, validateTuning } from './outliers-page';

const outlier = (over: Partial<Outlier> = {}): Outlier => ({
  id: 5, employeeCode: 'EMP-000005', name: 'Hiro Tanaka', country: 'JP', department: 'Engineering',
  jobTitle: 'Software Engineer', jobLevel: 'L3', currency: 'JPY', salaryMinor: 15_000_000, usdCents: 10_000_000,
  peerMedianCents: 5_000_000, peerCount: 8, deviationPct: 100, modifiedZ: 20.5, direction: 'HIGH', ...over,
});

const report = (over: Partial<OutlierReport> = {}): OutlierReport => ({
  employeesEvaluated: 7_670, employeesNotEvaluated: 1_844, groupsEvaluated: 669, totalFlagged: 1,
  params: { minGroupSize: 5, zThreshold: 3.5, minDeviationPct: 20 }, outliers: [outlier()], ...over,
});

describe('validateTuning', () => {
  const ok = { minGroupSize: 5, zThreshold: 3.5, minDeviationPct: 20 };

  it('accepts the defaults and the edges of each range', () => {
    expect(validateTuning(ok)).toBe('');
    expect(validateTuning({ minGroupSize: 3, zThreshold: 1, minDeviationPct: 0 })).toBe('');
    expect(validateTuning({ minGroupSize: 100, zThreshold: 10, minDeviationPct: 200 })).toBe('');
  });

  it('mirrors the server bounds', () => {
    expect(validateTuning({ ...ok, minGroupSize: 2 })).toContain('3 to 100');
    expect(validateTuning({ ...ok, minGroupSize: 5.5 })).toContain('whole number');
    expect(validateTuning({ ...ok, zThreshold: 0.5 })).toContain('between 1 and 10');
    expect(validateTuning({ ...ok, minDeviationPct: -1 })).toContain('0% and 200%');
    expect(validateTuning({ ...ok, minDeviationPct: NaN })).toContain('0% and 200%');
  });
});

describe('OutliersPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<OutliersPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OutliersPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OutliersPage);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const root = () => fixture.nativeElement as HTMLElement;
  const text = () => root().textContent ?? '';

  function reviewRequest(): TestRequest {
    TestBed.tick();
    return http.expectOne((r) => r.url === '/api/insights/outliers');
  }

  function flushLookups(): void {
    for (const req of http.match((r) => r.url.startsWith('/api/insights/by/'))) req.flush([]);
  }

  async function load(r: OutlierReport = report()): Promise<void> {
    flushLookups();
    reviewRequest().flush(r);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('asks for the default sensitivity, all directions, 50 rows', () => {
    flushLookups();
    const params = reviewRequest().request.params;

    expect(params.get('minGroupSize')).toBe('5');
    expect(params.get('zThreshold')).toBe('3.5');
    expect(params.get('minDeviationPct')).toBe('20');
    expect(params.get('direction')).toBe('any');
    expect(params.get('limit')).toBe('50');
    http.match((r) => r.url === '/api/insights/outliers').forEach((r) => r.flush(report()));
  });

  it('shows how many were flagged and, just as importantly, how many could not be judged', async () => {
    await load();

    expect(text()).toContain('Flagged');
    expect(text()).toContain('7,670');
    expect(text()).toContain('669 peer groups');
    expect(text()).toContain('1,844');
    expect(text()).toContain('peer group under 5 people');
  });

  it('lists each flagged person with pay in USD and local currency, the peer median and the gap', async () => {
    await load();

    expect(text()).toContain('Hiro Tanaka');
    expect(text()).toContain('Japan');
    expect(text()).toContain('$100,000');
    expect(text()).toContain('¥15,000,000');
    expect(text()).toContain('$50,000'); // peer median
    expect(text()).toContain('Above +100%');
    expect(root().querySelector('a[href="/employees/5"]')).not.toBeNull();
  });

  it('labels pay below peers in words, not only colour', async () => {
    await load(report({ outliers: [outlier({ direction: 'LOW', deviationPct: -45.5, currency: 'USD', usdCents: 5_000_000 })] }));

    expect(text()).toContain('Below -45.5%');
  });

  it('says when the list is truncated by the limit', async () => {
    await load(report({ totalFlagged: 193 }));

    expect(text()).toContain('Showing the 1 furthest from their peers');
  });

  it('explains an empty result, and distinguishes "all fine" from "nobody could be judged"', async () => {
    await load(report({ totalFlagged: 0, outliers: [] }));
    expect(text()).toContain('No one is flagged');
    expect(text()).toContain('within the normal range');
  });

  it('tells the user when no peer group was large enough', async () => {
    await load(report({ totalFlagged: 0, outliers: [], employeesEvaluated: 0, employeesNotEvaluated: 40, groupsEvaluated: 0 }));

    expect(text()).toContain('No peer group is large enough');
  });

  it('applies new sensitivity settings and refetches', async () => {
    await load();
    const page = fixture.componentInstance as unknown as Record<string, any>;
    page['minGroupSize'].setValue(8);
    page['zThreshold'].setValue(4);
    page['minDeviationPct'].setValue(30);

    page['applyTuning']();
    const params = reviewRequest().request.params;

    expect(params.get('minGroupSize')).toBe('8');
    expect(params.get('zThreshold')).toBe('4');
    expect(params.get('minDeviationPct')).toBe('30');
    http.match((r) => r.url === '/api/insights/outliers').forEach((r) => r.flush(report()));
  });

  it('refuses out-of-range settings without calling the server', async () => {
    await load();
    const page = fixture.componentInstance as unknown as Record<string, any>;
    page['minGroupSize'].setValue(1);

    page['applyTuning']();
    fixture.detectChanges();

    http.expectNone((r) => r.url === '/api/insights/outliers');
    expect(text()).toContain('3 to 100');
  });

  it('resets the settings to the defaults', async () => {
    await load();
    const page = fixture.componentInstance as unknown as Record<string, any>;
    page['minGroupSize'].setValue(9);
    page['applyTuning']();
    reviewRequest().flush(report());
    expect(page['isDefault']()).toBe(false);

    page['resetTuning']();
    const params = reviewRequest().request.params;

    expect(params.get('minGroupSize')).toBe('5');
    expect(page['isDefault']()).toBe(true);
    http.match((r) => r.url === '/api/insights/outliers').forEach((r) => r.flush(report()));
  });

  it('filters by direction', async () => {
    await load();

    fixture.componentInstance['direction'].set('low');

    expect(reviewRequest().request.params.get('direction')).toBe('low');
    http.match((r) => r.url === '/api/insights/outliers').forEach((r) => r.flush(report()));
  });

  it('shows the server message if the review cannot load', async () => {
    flushLookups();
    reviewRequest().flush({ detail: 'Database is locked' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(text()).toContain('Could not load the review');
    expect(text()).toContain('Database is locked');
  });
});
