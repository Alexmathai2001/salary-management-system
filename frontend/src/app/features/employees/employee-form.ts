import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { describeError } from '../../core/api-error';
import { EmployeeApi } from '../../core/employee.api';
import { FxApi } from '../../core/insights.api';
import { countryName, Lookups } from '../../core/lookups';
import { CreateEmployeeRequest, Employee } from '../../core/models';
import { fractionDigits, parseMajorToMinor, todayIso } from '../../core/money';

/** The server rejects amounts it cannot store exactly, so check the same rule here. */
function amountValidator(currency: () => string) {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value as string;
    if (!value) return null;
    const minor = parseMajorToMinor(value, currency());
    if (minor === null) return { format: true };
    return minor === 0 ? { zero: true } : null;
  };
}

/** Creates an employee, or (when an id is in the route) edits their profile. Salary is changed separately so it is always audited. */
@Component({
  selector: 'app-employee-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
  ],
  templateUrl: './employee-form.html',
  styleUrl: './employee-form.scss',
})
export class EmployeeForm implements OnInit {
  /** Route parameter; absent when creating. */
  readonly id = input<string>();

  private readonly api = inject(EmployeeApi);
  private readonly router = inject(Router);

  protected readonly options = toSignal(inject(Lookups).options$, { initialValue: null });
  protected readonly rates = toSignal(inject(FxApi).list(), { initialValue: [] });
  protected readonly today = todayIso();
  protected readonly saving = signal(false);
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly editing = computed(() => this.id() !== undefined);
  private existing: Employee | null = null;

  protected readonly form = inject(FormBuilder).nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(200)]],
    country: ['', [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)]],
    department: ['', [Validators.required, Validators.maxLength(100)]],
    jobTitle: ['', [Validators.required, Validators.maxLength(100)]],
    jobLevel: ['', [Validators.required, Validators.maxLength(10)]],
    hireDate: [this.today, [Validators.required, (c: AbstractControl) => (c.value > this.today ? { future: true } : null)]],
    currency: ['', Validators.required],
    salary: ['', [Validators.required, amountValidator((): string => this.form.controls.currency.value || 'USD')]],
  });

  protected readonly countryName = countryName;

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    const id = this.id();
    if (id === undefined) {
      return;
    }
    this.loading.set(true);
    this.api.get(+id).subscribe({
      next: (e) => {
        this.existing = e;
        this.form.patchValue({
          firstName: e.firstName,
          lastName: e.lastName,
          email: e.email,
          country: e.country,
          department: e.department,
          jobTitle: e.jobTitle,
          jobLevel: e.jobLevel,
          hireDate: e.hireDate,
        });
        this.form.controls.currency.disable();
        this.form.controls.salary.disable();
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(describeError(err).message);
      },
    });
  }

  protected countryPreview(): string {
    const code = this.form.controls.country.value.trim().toUpperCase();
    return code.length === 2 && this.form.controls.country.valid ? countryName(code) : '';
  }

  protected salaryError(): string {
    const errors = this.form.controls.salary.errors ?? {};
    if (errors['required']) return 'Enter the annual salary.';
    if (errors['zero']) return 'Salary must be greater than zero.';
    const currency = this.form.controls.currency.value;
    const digits = currency ? fractionDigits(currency) : 2;
    return digits === 0
      ? 'Enter a whole number (this currency has no decimals).'
      : `Enter a number with at most ${digits} decimal places.`;
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) {
      return;
    }
    const v = this.form.getRawValue();
    const profile = {
      firstName: v.firstName.trim(),
      lastName: v.lastName.trim(),
      email: v.email.trim(),
      country: v.country.trim().toUpperCase(),
      department: v.department.trim(),
      jobTitle: v.jobTitle.trim(),
      jobLevel: v.jobLevel.trim(),
      hireDate: v.hireDate,
    };
    this.saving.set(true);
    this.error.set('');

    const request$ = this.existing
      ? this.api.update(this.existing.id, profile)
      : this.api.create({
          ...profile,
          currency: v.currency,
          salaryMinor: parseMajorToMinor(v.salary, v.currency)!,
        } satisfies CreateEmployeeRequest);

    request$.subscribe({
      next: (saved) => void this.router.navigate(['/employees', saved.id]),
      error: (err) => {
        this.saving.set(false);
        const { message, fieldErrors } = describeError(err);
        this.applyServerErrors(message, fieldErrors);
        this.error.set(message);
      },
    });
  }

  /** Puts the server's per-field messages next to the matching inputs. */
  private applyServerErrors(message: string, fieldErrors: Record<string, string>): void {
    const controls = this.form.controls as Record<string, AbstractControl>;
    for (const [field, message] of Object.entries(fieldErrors)) {
      controls[field === 'salaryMinor' ? 'salary' : field]?.setErrors({ server: message });
    }
    // A 409 for a duplicate email carries no field map, only a message that names the email.
    if (/email/i.test(message) || fieldErrors['email']) {
      this.form.controls.email.setErrors({ server: fieldErrors['email'] ?? 'Already in use' });
    }
  }

}
