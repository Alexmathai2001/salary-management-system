import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of, startWith, Subject, switchMap } from 'rxjs';
import { describeError } from '../../core/api-error';
import { EmployeeApi } from '../../core/employee.api';
import { countryName } from '../../core/lookups';
import { Employee, SalaryHistoryEntry } from '../../core/models';
import { formatMinor, percentChange } from '../../core/money';
import { ConfirmDialog } from '../../shared/confirm-dialog';
import { ChangeSalaryDialog } from './change-salary-dialog';

type DetailState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; employee: Employee; history: SalaryHistoryEntry[] };

@Component({
  selector: 'app-employee-detail',
  imports: [RouterLink, MatButtonModule, MatTableModule, MatProgressBarModule],
  templateUrl: './employee-detail.html',
  styleUrl: './employee-detail.scss',
})
export class EmployeeDetail {
  /** Route parameter, bound by withComponentInputBinding(). */
  readonly id = input.required<string>();

  private readonly api = inject(EmployeeApi);
  private readonly dialog = inject(MatDialog);
  private readonly reload$ = new Subject<void>();

  protected readonly actionError = signal('');
  protected readonly columns = ['effectiveDate', 'change', 'pct', 'reason', 'recorded'];

  protected readonly state = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) =>
        this.reload$.pipe(
          startWith(undefined),
          switchMap(() =>
            forkJoin({ employee: this.api.get(+id), history: this.api.salaryHistory(+id) }).pipe(
              map((r): DetailState => ({ status: 'ready', ...r })),
              catchError((err) => of<DetailState>({ status: 'error', message: describeError(err).message })),
              startWith<DetailState>({ status: 'loading' }),
            ),
          ),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as DetailState },
  );

  protected readonly ready = computed(() => {
    const s = this.state();
    return s.status === 'ready' ? s : null;
  });

  protected readonly countryName = countryName;
  protected readonly formatMinor = formatMinor;
  protected readonly percentChange = percentChange;

  protected changeSalary(employee: Employee): void {
    this.dialog
      .open(ChangeSalaryDialog, { data: employee, autoFocus: 'dialog' })
      .afterClosed()
      .subscribe((updated) => {
        if (updated) {
          this.reload$.next();
        }
      });
  }

  protected deactivate(employee: Employee): void {
    this.dialog
      .open(ConfirmDialog, {
        data: {
          title: 'Deactivate employee?',
          message: `${employee.firstName} ${employee.lastName} will be hidden from the default directory and excluded from pay insights. Their salary history is kept and you can reactivate them later.`,
          confirmLabel: 'Deactivate',
        },
      })
      .afterClosed()
      .subscribe((confirmed) => confirmed && this.run(this.api.deactivate(employee.id)));
  }

  protected reactivate(employee: Employee): void {
    this.run(this.api.reactivate(employee.id));
  }

  private run(action: ReturnType<EmployeeApi['deactivate']>): void {
    this.actionError.set('');
    action.subscribe({
      next: () => this.reload$.next(),
      error: (err) => this.actionError.set(describeError(err).message),
    });
  }
}
