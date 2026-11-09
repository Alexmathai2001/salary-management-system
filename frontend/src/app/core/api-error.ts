import { HttpErrorResponse } from '@angular/common/http';

export interface ApiError {
  /** A message safe to show to the user. */
  message: string;
  /** field name -> message, from the server's validation response. */
  fieldErrors: Record<string, string>;
}

/** Turns any thrown value from an HTTP call into something the UI can display. */
export function describeError(err: unknown): ApiError {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return { message: 'Cannot reach the server. Check your connection and try again.', fieldErrors: {} };
    }
    const body = err.error as { detail?: string; errors?: Record<string, string> } | null;
    const detail = body && typeof body === 'object' ? body.detail : undefined;
    return {
      message: detail ?? `Request failed (${err.status}).`,
      fieldErrors: body && typeof body === 'object' && body.errors ? body.errors : {},
    };
  }
  return { message: 'Something went wrong. Please try again.', fieldErrors: {} };
}
