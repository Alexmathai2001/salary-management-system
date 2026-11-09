import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { describeError } from '../../core/api-error';
import { EmployeeApi } from '../../core/employee.api';
import { countryName, Lookups } from '../../core/lookups';
import { Employee, EmployeeQuery, PageResponse } from '../../core/models';
import { formatMinor } from '../../core/money';

type ListState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; page: PageResponse<Employee> };

/** Table column -> API sort key. */
const SORT_KEYS: Record<string, string> = {
  code: 'code',
  name: 'name',
  country: 'country',
  department: 'department',
  jobTitle: 'jobTitle',
  jobLevel: 'jobLevel',
  salary: 'salary',
  hireDate: 'hireDate',
};

@Component({
  selector: 'app-employee-list',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressBarModule,
  ],
  templateUrl: './employee-list.html',
  styleUrl: './employee-list.scss',
})
export class EmployeeList {
  private readonly api = inject(EmployeeApi);
  private readonly router = inject(Router);

  protected readonly options = toSignal(inject(Lookups).options$, { initialValue: null });
  protected readonly columns = ['code', 'name', 'country', 'department', 'jobTitle', 'jobLevel', 'salary', 'hireDate', 'status'];
  protected readonly pageSizes = [25, 50, 100];

  protected readonly search = new FormControl('', { nonNullable: true });

  protected readonly query = signal<EmployeeQuery>({
    page: 0,
    size: 25,
    sort: 'name',
    direction: 'asc',
    status: 'ACTIVE',
  });

  /** Salary sorting compares raw local amounts, so it is only meaningful within one currency (one country). */
  protected readonly salarySortable = computed(() => !!this.query().country);

  protected readonly state = toSignal(
    toObservable(this.query).pipe(
      switchMap((q) =>
        this.api.list(q).pipe(
          map((page): ListState => ({ status: 'ready', page })),
          catchError((err) => of<ListState>({ status: 'error', message: describeError(err).message })),
          startWith<ListState>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as ListState },
  );

  /** The CSV matches whatever the table is showing, minus paging. */
  protected readonly exportUrl = computed(() => this.api.exportUrl(this.query()));

  protected readonly hasFilters = computed(() => {
    const q = this.query();
    return !!(q.q || q.country || q.department || q.jobTitle || q.jobLevel) || q.status !== 'ACTIVE';
  });

  protected readonly countryName = countryName;
  protected readonly formatMinor = formatMinor;

  constructor() {
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((q) => this.patch({ q: q.trim() }));
  }

  protected setFilter(key: 'country' | 'department' | 'jobTitle' | 'jobLevel' | 'status', value: string): void {
    const patch: Partial<EmployeeQuery> = { [key]: value } as Partial<EmployeeQuery>;
    // Leaving single-country view makes salary sorting misleading, so fall back to the default sort.
    if (key === 'country' && !value && this.query().sort === 'salary') {
      patch.sort = 'name';
      patch.direction = 'asc';
    }
    this.patch(patch);
  }

  protected onSort(sort: Sort): void {
    if (!sort.direction) {
      this.patch({ sort: 'name', direction: 'asc' });
      return;
    }
    this.query.update((q) => ({
      ...q,
      sort: SORT_KEYS[sort.active] ?? 'name',
      direction: sort.direction as 'asc' | 'desc',
      page: 0,
    }));
  }

  protected onPage(event: PageEvent): void {
    this.query.update((q) => ({ ...q, page: event.pageIndex, size: event.pageSize }));
  }

  protected clearFilters(): void {
    this.search.setValue('', { emitEvent: false });
    this.query.update((q) => ({ page: 0, size: q.size, sort: 'name', direction: 'asc', status: 'ACTIVE' }));
  }

  protected open(employee: Employee): void {
    void this.router.navigate(['/employees', employee.id]);
  }

  protected sortColumn(): string {
    const sort = this.query().sort;
    return Object.keys(SORT_KEYS).find((c) => SORT_KEYS[c] === sort) ?? 'name';
  }

  private patch(change: Partial<EmployeeQuery>): void {
    this.query.update((q) => ({ ...q, ...change, page: 0 }));
  }
}
