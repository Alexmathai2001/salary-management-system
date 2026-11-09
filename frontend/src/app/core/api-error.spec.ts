import { HttpErrorResponse } from '@angular/common/http';
import { describeError } from './api-error';

describe('describeError', () => {
  it('uses the server detail and field errors from a problem response', () => {
    const err = new HttpErrorResponse({
      status: 400,
      error: { detail: 'Validation failed', errors: { salaryMinor: 'must be greater than zero' } },
    });

    expect(describeError(err)).toEqual({
      message: 'Validation failed',
      fieldErrors: { salaryMinor: 'must be greater than zero' },
    });
  });

  it('shows the detail of a conflict', () => {
    const err = new HttpErrorResponse({ status: 409, error: { detail: 'An employee with email a@b.c already exists' } });

    expect(describeError(err).message).toBe('An employee with email a@b.c already exists');
    expect(describeError(err).fieldErrors).toEqual({});
  });

  it('explains a network failure', () => {
    expect(describeError(new HttpErrorResponse({ status: 0 })).message).toContain('Cannot reach the server');
  });

  it('falls back to the status when the body has no detail', () => {
    expect(describeError(new HttpErrorResponse({ status: 500, error: 'boom' })).message).toBe('Request failed (500).');
  });

  it('copes with non-HTTP errors', () => {
    expect(describeError(new Error('x')).message).toBe('Something went wrong. Please try again.');
  });
});
