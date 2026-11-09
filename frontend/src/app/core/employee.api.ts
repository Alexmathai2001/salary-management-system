import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { toParams } from './api-params';
import {
  ChangeSalaryRequest,
  CreateEmployeeRequest,
  Employee,
  EmployeeQuery,
  PageResponse,
  SalaryHistoryEntry,
  UpdateEmployeeRequest,
} from './models';

@Injectable({ providedIn: 'root' })
export class EmployeeApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/employees';

  list(query: EmployeeQuery): Observable<PageResponse<Employee>> {
    return this.http.get<PageResponse<Employee>>(this.base, { params: toParams(query) });
  }

  get(id: number): Observable<Employee> {
    return this.http.get<Employee>(`${this.base}/${id}`);
  }

  salaryHistory(id: number): Observable<SalaryHistoryEntry[]> {
    return this.http.get<SalaryHistoryEntry[]>(`${this.base}/${id}/salary-history`);
  }

  create(request: CreateEmployeeRequest): Observable<Employee> {
    return this.http.post<Employee>(this.base, request);
  }

  update(id: number, request: UpdateEmployeeRequest): Observable<Employee> {
    return this.http.put<Employee>(`${this.base}/${id}`, request);
  }

  changeSalary(id: number, request: ChangeSalaryRequest): Observable<Employee> {
    return this.http.post<Employee>(`${this.base}/${id}/salary`, request);
  }

  deactivate(id: number): Observable<Employee> {
    return this.http.delete<Employee>(`${this.base}/${id}`);
  }

  reactivate(id: number): Observable<Employee> {
    return this.http.post<Employee>(`${this.base}/${id}/reactivate`, {});
  }
}
