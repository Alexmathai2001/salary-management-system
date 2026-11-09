import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Employee } from '../../core/models';
import { todayIso } from '../../core/money';
import { ChangeSalaryDialog } from './change-salary-dialog';

function employee(over: Partial<Employee> = {}): Employee {
  return {
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
    ...over,
  };
}

describe('ChangeSalaryDialog', () => {
  let http: HttpTestingController;
  let close: ReturnType<typeof vi.fn>;
  let fixture: ComponentFixture<ChangeSalaryDialog>;

  async function open(e: Employee = employee()): Promise<void> {
    close = vi.fn();
    await TestBed.configureTestingModule({
      imports: [ChangeSalaryDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MAT_DIALOG_DATA, useValue: e },
        { provide: MatDialogRef, useValue: { close } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ChangeSalaryDialog);
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  const form = () => fixture.componentInstance['form'];
  const submit = () => fixture.componentInstance['save']();

  it('sends the new salary in minor units with the date and trimmed reason', async () => {
    await open();
    form().patchValue({ amount: '9,000,000.50', effectiveDate: '2025-04-01', reason: '  Promotion ' });

    submit();

    const req = http.expectOne('/api/employees/7/salary');
    expect(req.request.body).toEqual({ newSalaryMinor: 900_000_050, effectiveDate: '2025-04-01', reason: 'Promotion' });
    const updated = employee({ salaryMinor: 900_000_050 });
    req.flush(updated);
    expect(close).toHaveBeenCalledWith(updated);
  });

  it('omits an empty reason', async () => {
    await open();
    form().patchValue({ amount: '9000000', reason: '   ' });

    submit();

    expect(http.expectOne('/api/employees/7/salary').request.body.reason).toBeUndefined();
  });

  it('does not submit an invalid form', async () => {
    await open();
    form().patchValue({ amount: '' });

    submit();

    http.expectNone('/api/employees/7/salary');
    expect(form().controls.amount.errors?.['required']).toBe(true);
  });

  it('rejects the same salary, zero and malformed amounts', async () => {
    await open();
    const amount = form().controls.amount;

    amount.setValue('8,300,000.00');
    expect(amount.errors?.['same']).toBe(true);
    amount.setValue('0');
    expect(amount.errors?.['zero']).toBe(true);
    amount.setValue('12abc');
    expect(amount.errors?.['format']).toBe(true);
    amount.setValue('9000000.123');
    expect(amount.errors?.['format']).toBe(true);
    amount.setValue('9000000');
    expect(amount.valid).toBe(true);
  });

  it('only accepts whole numbers for a currency without minor units', async () => {
    await open(employee({ currency: 'JPY', country: 'JP', salaryMinor: 5_000_000 }));
    const amount = form().controls.amount;

    amount.setValue('5500000.5');
    expect(amount.errors?.['format']).toBe(true);
    amount.setValue('5500000');
    expect(amount.valid).toBe(true);
    expect(fixture.componentInstance['amountError']()).toContain('no decimals');
  });

  it('rejects dates in the future or before the hire date', async () => {
    await open();
    const date = form().controls.effectiveDate;

    date.setValue('2999-01-01');
    expect(date.errors?.['future']).toBe(true);
    date.setValue('2021-02-28');
    expect(date.errors?.['beforeHire']).toBe(true);
    date.setValue('2021-03-01'); // hire date itself is allowed
    expect(date.valid).toBe(true);
    date.setValue(todayIso()); // so is today
    expect(date.valid).toBe(true);
  });

  it('shows the server message and stays open when saving fails', async () => {
    await open();
    form().patchValue({ amount: '9000000' });

    submit();
    http.expectOne('/api/employees/7/salary').flush(
      { detail: 'Cannot change the salary of an inactive employee' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(close).not.toHaveBeenCalled();
    expect(fixture.componentInstance['error']()).toBe('Cannot change the salary of an inactive employee');
    expect(fixture.componentInstance['saving']()).toBe(false);
  });

  it('ignores a second submit while the first is in flight', async () => {
    await open();
    form().patchValue({ amount: '9000000' });

    submit();
    submit();

    http.expectOne('/api/employees/7/salary'); // exactly one
  });
});
