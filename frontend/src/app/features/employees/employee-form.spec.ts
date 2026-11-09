import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Employee, PayStats } from '../../core/models';
import { EmployeeForm } from './employee-form';

const stat = (key: string): PayStats => ({ key, headcount: 1, totalCents: 0, avgCents: 0, medianCents: 0, p25Cents: 0, p75Cents: 0, minCents: 0, maxCents: 0 });

const existing: Employee = {
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

describe('EmployeeForm', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<EmployeeForm>;
  let navigate: ReturnType<typeof vi.spyOn>;

  async function create(id?: string): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [EmployeeForm],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(EmployeeForm);
    if (id) fixture.componentRef.setInput('id', id);
    fixture.detectChanges();
    for (const req of http.match((r) => r.url.startsWith('/api/insights/by/'))) req.flush([stat('x')]);
    http.expectOne('/api/fx-rates').flush([
      { currency: 'INR', ratePerUsd: 83, fractionDigits: 2, updatedAt: '' },
      { currency: 'JPY', ratePerUsd: 150, fractionDigits: 0, updatedAt: '' },
    ]);
  }

  afterEach(() => http.verify());

  const form = () => fixture.componentInstance['form'];
  const save = () => fixture.componentInstance['save']();

  function fillValid(): void {
    form().patchValue({
      firstName: ' Max ',
      lastName: 'Frei',
      email: 'max@acme.example',
      country: 'in',
      department: 'Sales',
      jobTitle: 'Account Executive',
      jobLevel: 'L3',
      hireDate: '2023-02-01',
      currency: 'INR',
      salary: '1,200,000.50',
    });
  }

  describe('adding an employee', () => {
    beforeEach(() => create());

    it('does not submit until the form is valid', () => {
      save();

      http.expectNone('/api/employees');
      expect(form().controls.firstName.errors?.['required']).toBe(true);
      expect(form().controls.salary.errors?.['required']).toBe(true);
    });

    it('submits normalised values with the salary in minor units, then opens the new employee', () => {
      fillValid();

      save();

      const req = http.expectOne('/api/employees');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        firstName: 'Max',
        lastName: 'Frei',
        email: 'max@acme.example',
        country: 'IN', // upper-cased
        department: 'Sales',
        jobTitle: 'Account Executive',
        jobLevel: 'L3',
        hireDate: '2023-02-01',
        currency: 'INR',
        salaryMinor: 120_000_050,
      });
      req.flush({ ...existing, id: 9 });
      expect(navigate).toHaveBeenCalledWith(['/employees', 9]);
    });

    it('validates country, email and hire date like the server does', () => {
      form().patchValue({ country: 'india', email: 'not-an-email', hireDate: '2999-01-01' });

      expect(form().controls.country.errors?.['pattern']).toBeTruthy();
      expect(form().controls.email.errors?.['email']).toBeTruthy();
      expect(form().controls.hireDate.errors?.['future']).toBe(true);
    });

    it('checks the salary against the chosen currency\'s decimals', () => {
      form().patchValue({ currency: 'JPY', salary: '5000000.5' });
      expect(form().controls.salary.errors?.['format']).toBe(true);

      form().patchValue({ salary: '5000000' });
      expect(form().controls.salary.valid).toBe(true);

      form().patchValue({ currency: 'INR', salary: '0' });
      expect(form().controls.salary.errors?.['zero']).toBe(true);
    });

    it('shows a duplicate-email conflict next to the email field and stays on the page', () => {
      fillValid();

      save();
      http.expectOne('/api/employees').flush(
        { detail: 'An employee with email max@acme.example already exists' },
        { status: 409, statusText: 'Conflict' },
      );

      expect(form().controls.email.errors?.['server']).toBeTruthy();
      expect(fixture.componentInstance['error']()).toContain('already exists');
      expect(navigate).not.toHaveBeenCalled();
      expect(fixture.componentInstance['saving']()).toBe(false);
    });

    it('maps server validation errors onto their fields', () => {
      fillValid();

      save();
      http.expectOne('/api/employees').flush(
        { detail: 'Validation failed', errors: { salaryMinor: 'must be greater than zero', jobLevel: 'too long' } },
        { status: 400, statusText: 'Bad Request' },
      );

      expect(form().controls.salary.errors?.['server']).toBe('must be greater than zero');
      expect(form().controls.jobLevel.errors?.['server']).toBe('too long');
    });

    it('does not double-submit', () => {
      fillValid();

      save();
      save();

      http.expectOne('/api/employees');
    });
  });

  describe('editing an employee', () => {
    beforeEach(async () => {
      await create('7');
      http.expectOne('/api/employees/7').flush(existing);
    });

    it('loads the profile and locks currency and salary', () => {
      expect(form().controls.firstName.value).toBe('Asha');
      expect(form().controls.country.value).toBe('IN');
      expect(form().controls.currency.disabled).toBe(true);
      expect(form().controls.salary.disabled).toBe(true);
    });

    it('saves profile changes with PUT and never sends a salary', () => {
      form().patchValue({ lastName: 'Rao-Iyer', department: 'Platform' });

      save();

      const req = http.expectOne('/api/employees/7');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body.lastName).toBe('Rao-Iyer');
      expect(req.request.body.salaryMinor).toBeUndefined();
      expect(req.request.body.currency).toBeUndefined();
      req.flush({ ...existing, lastName: 'Rao-Iyer' });
      expect(navigate).toHaveBeenCalledWith(['/employees', 7]);
    });
  });
});
