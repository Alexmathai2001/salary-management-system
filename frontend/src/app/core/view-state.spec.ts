import { HttpErrorResponse } from '@angular/common/http';
import { Subject, firstValueFrom, scan, throwError, of, toArray } from 'rxjs';
import { ViewEvent, initialView, loadAsEvents, reduceView } from './view-state';

describe('view state', () => {
  it('starts loading with no data', () => {
    expect(initialView<number>()).toEqual({ data: null, loading: true, error: '' });
  });

  it('replaces the data when a request succeeds', () => {
    const loading = reduceView({ data: 1, loading: true, error: '' }, { type: 'ok', data: 2 });

    expect(loading).toEqual({ data: 2, loading: false, error: '' });
  });

  it('keeps the previous data while a refetch is loading', () => {
    const next = reduceView({ data: 1, loading: false, error: '' }, { type: 'loading' });

    expect(next).toEqual({ data: 1, loading: true, error: '' });
  });

  it('keeps the previous data when a refetch fails, and records the message', () => {
    const next = reduceView({ data: 1, loading: true, error: '' }, { type: 'error', message: 'boom' });

    expect(next).toEqual({ data: 1, loading: false, error: 'boom' });
  });

  it('clears an old error when the next request starts', () => {
    const next = reduceView({ data: 1, loading: false, error: 'boom' }, { type: 'loading' });

    expect(next.error).toBe('');
  });

  describe('loadAsEvents', () => {
    it('emits loading, then the data', async () => {
      const events = await firstValueFrom(loadAsEvents(of('x')).pipe(toArray()));

      expect(events).toEqual([{ type: 'loading' }, { type: 'ok', data: 'x' }]);
    });

    it('turns an HTTP failure into a displayable error event instead of throwing', async () => {
      const failing = throwError(() => new HttpErrorResponse({ status: 500, error: { detail: 'Database is locked' } }));

      const events = await firstValueFrom(loadAsEvents(failing).pipe(toArray()));

      expect(events).toEqual([{ type: 'loading' }, { type: 'error', message: 'Database is locked' }]);
    });

    it('drives a reducer end to end: first load, refetch, failed refetch', () => {
      const source = new Subject<ViewEvent<string>>();
      let state = initialView<string>();
      source.pipe(scan(reduceView<string>, initialView<string>())).subscribe((s) => (state = s));

      source.next({ type: 'ok', data: 'first' });
      source.next({ type: 'loading' });
      expect(state).toEqual({ data: 'first', loading: true, error: '' });
      source.next({ type: 'error', message: 'down' });
      expect(state).toEqual({ data: 'first', loading: false, error: 'down' });
    });
  });
});
