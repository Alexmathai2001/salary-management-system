/** Types mirroring the Spring API. Money fields are integers: salaryMinor in the currency's minor units, *Cents in USD cents. */

export type EmployeeStatus = 'ACTIVE' | 'INACTIVE';

export interface Employee {
  id: number;
  employeeCode: string;
  firstName: string;
  lastName: string;
  email: string;
  country: string;
  department: string;
  jobTitle: string;
  jobLevel: string;
  currency: string;
  salaryMinor: number;
  hireDate: string; // ISO date, e.g. 2023-02-01
  status: EmployeeStatus;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface EmployeeQuery {
  q?: string;
  country?: string;
  department?: string;
  jobTitle?: string;
  jobLevel?: string;
  status?: EmployeeStatus | '';
  page?: number;
  size?: number;
  sort?: string;
  direction?: 'asc' | 'desc';
}

export interface CreateEmployeeRequest {
  firstName: string;
  lastName: string;
  email: string;
  country: string;
  department: string;
  jobTitle: string;
  jobLevel: string;
  currency: string;
  salaryMinor: number;
  hireDate: string;
}

export type UpdateEmployeeRequest = Omit<CreateEmployeeRequest, 'currency' | 'salaryMinor'>;

export interface ChangeSalaryRequest {
  newSalaryMinor: number;
  effectiveDate: string;
  reason?: string;
}

export interface SalaryHistoryEntry {
  id: number;
  oldSalaryMinor: number | null;
  newSalaryMinor: number;
  currency: string;
  effectiveDate: string;
  reason: string | null;
  changedAt: string;
}

export interface FxRate {
  currency: string;
  ratePerUsd: number;
  fractionDigits: number;
  updatedAt: string;
}

export interface PayStats {
  key: string;
  headcount: number;
  totalCents: number;
  avgCents: number;
  medianCents: number;
  p25Cents: number;
  p75Cents: number;
  minCents: number;
  maxCents: number;
}

export type Dimension = 'country' | 'department' | 'jobLevel' | 'jobTitle';

export interface InsightsFilter {
  country?: string;
  department?: string;
  jobLevel?: string;
  jobTitle?: string;
  includeInactive?: boolean;
}

export interface DistributionBucket {
  fromUsd: number;
  toUsd: number;
  count: number;
}

export interface Earner {
  id: number;
  employeeCode: string;
  name: string;
  country: string;
  department: string;
  jobTitle: string;
  jobLevel: string;
  currency: string;
  salaryMinor: number;
  usdCents: number;
}

export interface Outlier extends Earner {
  peerMedianCents: number;
  peerCount: number;
  deviationPct: number;
  modifiedZ: number | null;
  direction: 'HIGH' | 'LOW';
}

export interface OutlierParams {
  minGroupSize: number;
  zThreshold: number;
  minDeviationPct: number;
}

export interface OutlierReport {
  employeesEvaluated: number;
  employeesNotEvaluated: number;
  groupsEvaluated: number;
  totalFlagged: number;
  params: OutlierParams;
  outliers: Outlier[];
}

export interface OutlierQuery extends InsightsFilter, Partial<OutlierParams> {
  direction?: 'any' | 'high' | 'low';
  limit?: number;
}
