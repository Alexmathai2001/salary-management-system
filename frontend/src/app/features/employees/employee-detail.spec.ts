import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Employee, SalaryHistoryEntry } from '../../core/models';
import { EmployeeDetail } from './employee-detail';

const asha: Employee = {
  id: 7,
  employeeCode: 'EMP-000007',
  firstName: 'Asha',
  lastName: 'Rao',
  email: 'asha@acme.example',
  country: 'IN',
  department: 'Engineering',
  jobTitle: 'Backend Engineer',
  jobLevel: 'L4',
  currency: 'INR',
  salaryMinor: 830_000_000,
  hireDate: '2021-03-01',
  status: 'ACTIVE',
};

const history: SalaryHistoryEntry[] = [
  { id: 2, oldSalaryMinor: 800_000_000, newSalaryMinor: 830_000_000, currency: 'INR', effectiveDate: '2024-04-01', reason: 'Annual review', changedAt: '2024-04-02T09:00:00' },
  { id: 1, oldSalaryMinor: null, newSalaryMinor: 800_000_000, currency: 'INR', effectiveDate: '2021-03-01', reason: 'Initial salary', changedAt: '2021-03-01T09:00:00' },
];

describe('EmployeeDetail', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<EmployeeDetail>;
  let dialogOpen: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    dialogOpen = vi.fn();
    await TestBed.configureTestingModule({
      imports: [EmployeeDetail],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialog, useValue: { open: dialogOpen } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmployeeDetail);
    fixture.componentRef.setInput('id', '7');
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function flushLoad(employee: Employee = asha): void {
    TestBed.tick();
    http.expectOne('/api/employees/7').flush(employee);
    http.expectOne('/api/employees/7/salary-history').flush(history);
  }

  async function settle(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const button = (label: string) =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('button, a')).find((b) =>
      b.textContent?.includes(label),
    );

  it('shows the profile, current salary and salary history', async () => {
    flushLoad();
    await settle();

    expect(text()).toContain('Asha Rao');
    expect(text()).toContain('Backend Engineer');
    expect(text()).toContain('₹8,300,000.00');
    expect(text()).toContain('India');
    expect(text()).toContain('+3.8%'); // 800M -> 830M paise
    expect(text()).toContain('Starting salary');
    expect(text()).toContain('Annual review');
  });

  it('offers Reactivate (not Change salary) for an inactive employee', async () => {
    flushLoad({ ...asha, status: 'INACTIVE' });
    await settle();

    expect(button('Reactivate')).toBeTruthy();
    expect(button('Change salary')).toBeUndefined();
    expect(text()).toContain('Inactive');
  });

  it('reloads after a salary change is saved in the dialog', async () => {
    flushLoad();
    await settle();
    dialogOpen.mockReturnValue({ afterClosed: () => of({ ...asha, salaryMinor: 900_000_000 }) });

    button('Change salary')!.click();
    TestBed.tick();

    http.expectOne('/api/employees/7').flush({ ...asha, salaryMinor: 900_000_000 });
    http.expectOne('/api/employees/7/salary-history').flush(history);
    await settle();
    expect(text()).toContain('₹9,000,000.00');
  });

  it('does nothing when the salary dialog is cancelled', async () => {
    flushLoad();
    await settle();
    dialogOpen.mockReturnValue({ afterClosed: () => of(undefined) });

    button('Change salary')!.click();
    TestBed.tick();

    http.expectNone('/api/employees/7');
  });

  it('asks before deactivating, then deactivates and reloads', async () => {
    flushLoad();
    await settle();
    dialogOpen.mockReturnValue({ afterClosed: () => of(true) });

    button('Deactivate')!.click();

    const del: TestRequest = http.expectOne('/api/employees/7');
    expect(del.request.method).toBe('DELETE');
    del.flush({ ...asha, status: 'INACTIVE' });
    TestBed.tick();
    http.expectOne('/api/employees/7').flush({ ...asha, status: 'INACTIVE' });
    http.expectOne('/api/employees/7/salary-history').flush(history);
    await settle();
    expect(button('Reactivate')).toBeTruthy();
  });

  it('does not deactivate when the confirmation is declined', async () => {
    flushLoad();
    await settle();
    dialogOpen.mockReturnValue({ afterClosed: () => of(false) });

    button('Deactivate')!.click();

    http.expectNone('/api/employees/7');
  });

  it('shows the server message when the employee does not exist', async () => {
    TestBed.tick();
    http.expectOne('/api/employees/7').flush({ detail: 'Employee 7 not found' }, { status: 404, statusText: 'Not Found' });
    // forkJoin abandons the sibling request as soon as one fails, so the history call is cancelled, not answered.
    expect(http.expectOne('/api/employees/7/salary-history').cancelled).toBe(true);
    await settle();

    expect(text()).toContain('Employee 7 not found');
  });
});
