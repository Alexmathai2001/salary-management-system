import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { describeError } from '../../core/api-error';
import { EmployeeApi } from '../../core/employee.api';
import { Employee } from '../../core/models';
import { formatMinor, fractionDigits, parseMajorToMinor, todayIso } from '../../core/money';

/** Records a salary change. The server stores old/new/effective date/reason as an audit entry. */
@Component({
  selector: 'app-change-salary-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>Change salary</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="content">
        <p class="muted">
          {{ employee.firstName }} {{ employee.lastName }} currently earns
          <strong>{{ current }}</strong> a year.
        </p>

        <mat-form-field>
          <mat-label>New annual salary ({{ employee.currency }})</mat-label>
          <input matInput formControlName="amount" inputmode="decimal" autocomplete="off" cdkFocusInitial />
          @if (form.controls.amount.touched && form.controls.amount.errors; as errors) {
            <mat-error>{{ amountError() }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field>
          <mat-label>Effective date</mat-label>
          <input matInput type="date" formControlName="effectiveDate" [min]="employee.hireDate" [max]="today" />
          @if (form.controls.effectiveDate.errors; as errors) {
            <mat-error>{{ dateError() }}</mat-error>
          }
        </mat-form-field>

        <mat-form-field>
          <mat-label>Reason (optional)</mat-label>
          <textarea matInput formControlName="reason" rows="2" maxlength="500"></textarea>
        </mat-form-field>

        @if (error()) {
          <div class="error-banner" role="alert">{{ error() }}</div>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button type="submit" [disabled]="saving()">{{ saving() ? 'Saving…' : 'Save change' }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .content {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: min(420px, 80vw);
    }
  `,
})
export class ChangeSalaryDialog {
  protected readonly employee = inject<Employee>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<ChangeSalaryDialog, Employee>>(MatDialogRef);
  private readonly api = inject(EmployeeApi);

  protected readonly today = todayIso();
  protected readonly current = formatMinor(this.employee.salaryMinor, this.employee.currency);
  protected readonly saving = signal(false);
  protected readonly error = signal('');

  protected readonly form = inject(FormBuilder).nonNullable.group({
    amount: ['', [Validators.required, (c: { value: string }) => this.amountValidator(c.value)]],
    effectiveDate: [this.today, [Validators.required, (c: { value: string }) => this.dateValidator(c.value)]],
    reason: [''],
  });

  protected amountError(): string {
    const errors = this.form.controls.amount.errors ?? {};
    if (errors['required']) return 'Enter the new salary.';
    if (errors['same']) return 'This is the same as the current salary.';
    if (errors['zero']) return 'Salary must be greater than zero.';
    const digits = fractionDigits(this.employee.currency);
    return digits === 0
      ? `${this.employee.currency} has no decimals. Enter a whole number.`
      : `Enter a number with at most ${digits} decimal places.`;
  }

  protected dateError(): string {
    const errors = this.form.controls.effectiveDate.errors ?? {};
    if (errors['required']) return 'Choose a date.';
    if (errors['future']) return 'The date cannot be in the future.';
    return 'The date cannot be before the hire date.';
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) {
      return;
    }
    const { amount, effectiveDate, reason } = this.form.getRawValue();
    this.saving.set(true);
    this.error.set('');
    this.api
      .changeSalary(this.employee.id, {
        newSalaryMinor: parseMajorToMinor(amount, this.employee.currency)!,
        effectiveDate,
        reason: reason.trim() || undefined,
      })
      .subscribe({
        next: (updated) => this.ref.close(updated),
        error: (err) => {
          this.saving.set(false);
          this.error.set(describeError(err).message);
        },
      });
  }

  private amountValidator(value: string) {
    if (!value) return null; // `required` reports this
    const minor = parseMajorToMinor(value, this.employee.currency);
    if (minor === null) return { format: true };
    if (minor === 0) return { zero: true };
    if (minor === this.employee.salaryMinor) return { same: true };
    return null;
  }

  private dateValidator(value: string) {
    if (!value) return null;
    if (value > this.today) return { future: true };
    if (value < this.employee.hireDate) return { beforeHire: true };
    return null;
  }
}
