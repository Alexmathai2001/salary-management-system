import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { EmployeeApi } from './employee.api';
import { InsightsApi } from './insights.api';

describe('API services', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists employees sending only the filters that are set', () => {
    TestBed.inject(EmployeeApi).list({ q: 'rao', country: 'IN', department: '', page: 0, size: 25 }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/employees');
    expect(req.request.params.get('q')).toBe('rao');
    expect(req.request.params.get('country')).toBe('IN');
    expect(req.request.params.get('page')).toBe('0'); // zero is a real value, not "blank"
    expect(req.request.params.has('department')).toBe(false);
    expect(req.request.params.has('status')).toBe(false);
    req.flush({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
  });

  it('changes a salary with POST to the dedicated endpoint', () => {
    TestBed.inject(EmployeeApi)
      .changeSalary(7, { newSalaryMinor: 2_400_000, effectiveDate: '2025-04-01', reason: 'Promotion' })
      .subscribe();

    const req = http.expectOne('/api/employees/7/salary');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.newSalaryMinor).toBe(2_400_000);
    req.flush({});
  });

  it('deactivates with DELETE and reactivates with POST', () => {
    const api = TestBed.inject(EmployeeApi);
    api.deactivate(3).subscribe();
    expect(http.expectOne('/api/employees/3').request.method).toBe('DELETE');
    api.reactivate(3).subscribe();
    expect(http.expectOne('/api/employees/3/reactivate').request.method).toBe('POST');
  });

  it('requests insights by dimension with filters', () => {
    TestBed.inject(InsightsApi).by('jobLevel', { country: 'IN', includeInactive: false }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/insights/by/jobLevel');
    expect(req.request.params.get('country')).toBe('IN');
    expect(req.request.params.get('includeInactive')).toBe('false');
    req.flush([]);
  });

  it('passes outlier tuning parameters through', () => {
    TestBed.inject(InsightsApi).outliers({ minGroupSize: 8, direction: 'high', limit: 20 }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/insights/outliers');
    expect(req.request.params.get('minGroupSize')).toBe('8');
    expect(req.request.params.get('direction')).toBe('high');
    req.flush({});
  });
});
