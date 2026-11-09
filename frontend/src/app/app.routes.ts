import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'employees' },
  {
    path: 'employees',
    title: 'Employees · ACME Salary Management',
    loadComponent: () => import('./features/employees/employee-list').then((m) => m.EmployeeList),
  },
  {
    path: 'employees/new',
    title: 'Add employee · ACME Salary Management',
    loadComponent: () => import('./features/employees/employee-form').then((m) => m.EmployeeForm),
  },
  {
    path: 'employees/:id/edit',
    title: 'Edit employee · ACME Salary Management',
    loadComponent: () => import('./features/employees/employee-form').then((m) => m.EmployeeForm),
  },
  {
    path: 'employees/:id',
    title: 'Employee · ACME Salary Management',
    loadComponent: () => import('./features/employees/employee-detail').then((m) => m.EmployeeDetail),
  },
  {
    path: 'insights',
    title: 'Pay insights · ACME Salary Management',
    loadComponent: () => import('./features/insights/insights-page').then((m) => m.InsightsPage),
  },
  {
    path: 'outliers',
    title: 'Pay outliers · ACME Salary Management',
    loadComponent: () => import('./features/outliers/outliers-page').then((m) => m.OutliersPage),
  },
  {
    path: 'fx-rates',
    title: 'FX rates · ACME Salary Management',
    loadComponent: () => import('./features/fx/fx-rates-page').then((m) => m.FxRatesPage),
  },
  { path: '**', redirectTo: 'employees' },
];
