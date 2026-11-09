import { HttpParams } from '@angular/common/http';

/** Builds query params, skipping null, undefined and blank values so URLs stay clean. */
export function toParams(values: object): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value === undefined || value === null || value === '') {
      continue;
    }
    params = params.set(key, String(value));
  }
  return params;
}
