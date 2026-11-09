import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Employee, PageResponse, PayStats } from '../../core/models';
import { EmployeeList } from './employee-list';

function employee(over: Partial<Employee> = {}): Employee {
  return {
    id: 1,
    employeeCode: 'EMP-000001',
    firstName: 'Asha',
    lastName: 'Rao',
    email: 'asha.rao@acme.example',
    country: 'IN',
    department: 'Engineering',
    jobTitle: 'Backend Engineer',
    jobLevel: 'L4',
    currency: 'INR',
    salaryMinor: 830_000_000,
    hireDate: '2021-03-01',
    status: 'ACTIVE',
    ...over,
  };
}

function page(content: Employee[], totalElements = content.length): PageResponse<Employee> {
  return { content, page: 0, size: 25, totalElements, totalPages: Math.ceil(totalElements / 25) };
}

function stat(key: string): PayStats {
  return { key, headcount: 1, totalCents: 0, avgCents: 0, medianCents: 0, p25Cents: 0, p75Cents: 0, minCents: 0, maxCents: 0 };
}

describe('EmployeeList', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<EmployeeList>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmployeeList],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmployeeList);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  /** The dropdown option lists come from four insight calls. */
  function flushLookups(): void {
    const byDimension: Record<string, string[]> = {
      country: ['IN', 'US'],
      department: ['Engineering', 'Sales'],
      jobLevel: ['L1', 'L2'],
      jobTitle: ['Backend Engineer'],
    };
    for (const req of http.match((r) => r.url.startsWith('/api/insights/by/'))) {
      const dimension = req.request.url.split('/').pop()!;
      req.flush((byDimension[dimension] ?? []).map(stat));
    }
  }

  /**
   * The component is zoneless: a state change reaches the HTTP layer through an effect, which only runs when change
   * detection does. In the browser that is automatic; in a test we flush effects explicitly before looking for requests.
   */
  function listRequest(): TestRequest {
    TestBed.tick();
    return http.expectOne((r) => r.url === '/api/employees');
  }

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('requests the first page of active employees sorted by name', async () => {
    flushLookups();
    const req = listRequest();

    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('25');
    expect(req.request.params.get('sort')).toBe('name');
    expect(req.request.params.get('direction')).toBe('asc');
    expect(req.request.params.get('status')).toBe('ACTIVE');
    req.flush(page([]));
    await settle();
  });

  it('renders each employee with a local-currency salary', async () => {
    flushLookups();
    listRequest().flush(
      page([employee(), employee({ id: 2, firstName: 'Hiro', lastName: 'Tanaka', currency: 'JPY', salaryMinor: 5_000_000, country: 'JP' })]),
    );
    await settle();

    expect(text()).toContain('Asha Rao');
    expect(text()).toContain('asha.rao@acme.example');
    expect(text()).toContain('8,300,000.00'); // INR, two decimals
    expect(text()).toContain('5,000,000'); // JPY, no decimals
    expect(text()).toContain('Japan');
  });

  it('says so when nothing matches', async () => {
    flushLookups();
    listRequest().flush(page([]));
    await settle();

    expect(text()).toContain('No employees match');
  });

  it('shows the server message when loading fails, and can retry', async () => {
    flushLookups();
    listRequest().flush({ detail: 'Database is locked' }, { status: 500, statusText: 'Server Error' });
    await settle();

    expect(text()).toContain('Could not load employees');
    expect(text()).toContain('Database is locked');

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.error-banner button')!.click();
    listRequest().flush(page([employee()]));
    await settle();
    expect(text()).toContain('Asha Rao');
  });

  it('filters by country and returns to the first page', async () => {
    flushLookups();
    listRequest().flush(page([employee()], 100));
    await settle();
    fixture.componentInstance['onPage']({ pageIndex: 2, pageSize: 25, length: 100 });
    listRequest().flush(page([employee()], 100));

    fixture.componentInstance['setFilter']('country', 'IN');
    const req = listRequest();

    expect(req.request.params.get('country')).toBe('IN');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([]));
  });

  it('requests the chosen page and page size', async () => {
    flushLookups();
    listRequest().flush(page([employee()], 100));
    await settle();

    fixture.componentInstance['onPage']({ pageIndex: 1, pageSize: 50, length: 100 });
    const req = listRequest();

    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('50');
    req.flush(page([]));
  });

  it('waits for typing to pause before searching', async () => {
    vi.useFakeTimers();
    flushLookups();
    listRequest().flush(page([]));
    await settle();

    const search = fixture.componentInstance['search'];
    search.setValue('r');
    await vi.advanceTimersByTimeAsync(100);
    search.setValue('ra');
    await vi.advanceTimersByTimeAsync(100);
    search.setValue('  rao ');
    TestBed.tick();
    http.expectNone((r) => r.url === '/api/employees'); // still typing: no request yet
    await vi.advanceTimersByTimeAsync(300);

    const req = listRequest(); // exactly one request, trimmed
    expect(req.request.params.get('q')).toBe('rao');
    req.flush(page([]));
  });

  it('only allows sorting by salary once a single country is chosen', async () => {
    flushLookups();
    listRequest().flush(page([employee()]));
    await settle();
    const component = fixture.componentInstance;

    expect(component['salarySortable']()).toBe(false);

    component['setFilter']('country', 'IN');
    listRequest().flush(page([employee()]));
    expect(component['salarySortable']()).toBe(true);

    component['onSort']({ active: 'salary', direction: 'desc' });
    const sorted = listRequest();
    expect(sorted.request.params.get('sort')).toBe('salary');
    expect(sorted.request.params.get('direction')).toBe('desc');
    sorted.flush(page([employee()]));

    // Leaving the single-country view must not leave a misleading cross-currency salary sort behind.
    component['setFilter']('country', '');
    const reset = listRequest();
    expect(reset.request.params.get('sort')).toBe('name');
    expect(reset.request.params.has('country')).toBe(false);
    reset.flush(page([]));
  });

  it('clears all filters back to the defaults', async () => {
    flushLookups();
    listRequest().flush(page([]));
    await settle();
    const component = fixture.componentInstance;
    component['setFilter']('department', 'Sales');
    listRequest().flush(page([]));
    component['setFilter']('status', 'INACTIVE');
    listRequest().flush(page([]));

    component['clearFilters']();
    const req = listRequest();

    expect(req.request.params.has('department')).toBe(false);
    expect(req.request.params.get('status')).toBe('ACTIVE');
    req.flush(page([]));
  });

  it('offers a CSV export of exactly what is being shown, without paging', async () => {
    flushLookups();
    listRequest().flush(page([employee()]));
    await settle();
    const link = () => (fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>('a[download]')!;
    expect(link().textContent).toContain('Export CSV');
    expect(link().getAttribute('href')).toContain('/api/employees/export.csv?');
    expect(link().getAttribute('href')).toContain('status=ACTIVE');

    fixture.componentInstance['setFilter']('country', 'IN');
    listRequest().flush(page([]));
    await settle();

    expect(link().getAttribute('href')).toContain('country=IN');
    expect(link().getAttribute('href')).not.toContain('page=');
  });

  it('opens the employee page when a row is chosen', async () => {
    flushLookups();
    listRequest().flush(page([employee({ id: 42 })]));
    await settle();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('tr.clickable')!.click();

    expect(navigate).toHaveBeenCalledWith(['/employees', 42]);
  });
});
