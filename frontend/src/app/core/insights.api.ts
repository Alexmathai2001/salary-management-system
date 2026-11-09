import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { toParams } from './api-params';
import {
  Dimension,
  DistributionBucket,
  Earner,
  FxRate,
  InsightsFilter,
  OutlierQuery,
  OutlierReport,
  PayStats,
} from './models';

@Injectable({ providedIn: 'root' })
export class InsightsApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/insights';

  summary(filter: InsightsFilter = {}): Observable<PayStats> {
    return this.http.get<PayStats>(`${this.base}/summary`, { params: toParams(filter) });
  }

  by(dimension: Dimension, filter: InsightsFilter = {}): Observable<PayStats[]> {
    return this.http.get<PayStats[]>(`${this.base}/by/${dimension}`, { params: toParams(filter) });
  }

  distribution(bucketUsd: number, filter: InsightsFilter = {}): Observable<DistributionBucket[]> {
    return this.http.get<DistributionBucket[]>(`${this.base}/distribution`, {
      params: toParams({ ...filter, bucketUsd }),
    });
  }

  earners(order: 'top' | 'bottom', limit: number, filter: InsightsFilter = {}): Observable<Earner[]> {
    return this.http.get<Earner[]>(`${this.base}/earners`, { params: toParams({ ...filter, order, limit }) });
  }

  outliers(query: OutlierQuery): Observable<OutlierReport> {
    return this.http.get<OutlierReport>(`${this.base}/outliers`, { params: toParams(query) });
  }
}

@Injectable({ providedIn: 'root' })
export class FxApi {
  private readonly http = inject(HttpClient);

  list(): Observable<FxRate[]> {
    return this.http.get<FxRate[]>('/api/fx-rates');
  }

  update(currency: string, ratePerUsd: number): Observable<FxRate> {
    return this.http.put<FxRate>(`/api/fx-rates/${currency}`, { ratePerUsd });
  }
}
