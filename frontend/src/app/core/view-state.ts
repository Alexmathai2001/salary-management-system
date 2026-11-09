import { Observable, catchError, map, of, startWith } from 'rxjs';
import { describeError } from './api-error';

/**
 * State of a data-driven view. `data` survives loading and error events, so a refetch never blanks the page: the
 * previous render stays on screen (dimmed by the view) until the new data arrives.
 */
export interface ViewState<T> {
  data: T | null;
  loading: boolean;
  error: string;
}

export type ViewEvent<T> = { type: 'loading' } | { type: 'ok'; data: T } | { type: 'error'; message: string };

export function initialView<T>(): ViewState<T> {
  return { data: null, loading: true, error: '' };
}

export function reduceView<T>(prev: ViewState<T>, event: ViewEvent<T>): ViewState<T> {
  switch (event.type) {
    case 'ok':
      return { data: event.data, loading: false, error: '' };
    case 'error':
      return { ...prev, loading: false, error: event.message };
    default:
      return { ...prev, loading: true, error: '' };
  }
}

/** One request as view events: loading, then either the data or a displayable error. */
export function loadAsEvents<T>(request: Observable<T>): Observable<ViewEvent<T>> {
  return request.pipe(
    map((data): ViewEvent<T> => ({ type: 'ok', data })),
    catchError((err) => of<ViewEvent<T>>({ type: 'error', message: describeError(err).message })),
    startWith<ViewEvent<T>>({ type: 'loading' }),
  );
}
