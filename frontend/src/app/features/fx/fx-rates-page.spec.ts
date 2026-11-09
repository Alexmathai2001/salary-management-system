import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FxRate } from '../../core/models';
import { FxRatesPage, validateRate } from './fx-rates-page';

const rates: FxRate[] = [
  { currency: 'INR', ratePerUsd: 83, fractionDigits: 2, updatedAt: '1970-01-01T00:00:00' },
  { currency: 'JPY', ratePerUsd: 150, fractionDigits: 0, updatedAt: '2025-06-01T09:30:00' },
  { currency: 'USD', ratePerUsd: 1, fractionDigits: 2, updatedAt: '1970-01-01T00:00:00' },
];

describe('validateRate', () => {
  it('accepts positive decimals with up to six places', () => {
    for (const ok of ['83', '83.25', '0.000001', ' 150 ', '1.123456']) {
      expect(validateRate(ok)).toBe('');
    }
  });

  it('rejects everything else with a reason', () => {
    expect(validateRate('')).toContain('positive number');
    expect(validateRate('abc')).toContain('positive number');
    expect(validateRate('-3')).toContain('positive number');
    expect(validateRate('0')).toContain('greater than zero');
    expect(validateRate('0.0')).toContain('greater than zero');
    expect(validateRate('1.1234567')).toContain('6 decimal places');
  });
});

describe('FxRatesPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<FxRatesPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FxRatesPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(FxRatesPage);
    fixture.detectChanges();
    http.expectOne('/api/fx-rates').flush(rates);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const root = () => fixture.nativeElement as HTMLElement;
  const text = () => root().textContent ?? '';
  const page = () => fixture.componentInstance as unknown as Record<string, (...a: unknown[]) => unknown> & {
    control: (r: FxRate) => import('@angular/forms').FormControl<string>;
    newCode: import('@angular/forms').FormControl<string>;
    newRate: import('@angular/forms').FormControl<string>;
  };

  it('lists each currency with its name and rate, and treats USD as fixed', () => {
    expect(text()).toContain('Indian Rupee');
    expect(text()).toContain('Japanese Yen');
    expect(text()).toContain('1 (reporting base)');
    expect(root().querySelectorAll('tbody tr')).toHaveLength(3);
    expect(root().querySelectorAll('tbody input')).toHaveLength(2); // no input for USD
    expect(text()).toContain('2025-06-01');
    expect(text()).toContain('Initial value'); // the 1970 placeholder is not shown as a date
  });

  it('enables Save only after the rate is changed', async () => {
    const save = () => root().querySelectorAll<HTMLButtonElement>('tbody button')[0];
    expect(save().disabled).toBe(true);

    page().control(rates[0]).setValue('84.5');
    fixture.detectChanges();

    expect(save().disabled).toBe(false);
  });

  it('saves a changed rate, confirms it, and reloads', () => {
    page().control(rates[0]).setValue('84.5');

    (page()['save'] as (r: FxRate) => void)(rates[0]);

    const put = http.expectOne('/api/fx-rates/INR');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ ratePerUsd: 84.5 });
    put.flush({ ...rates[0], ratePerUsd: 84.5 });
    http.expectOne('/api/fx-rates').flush([{ ...rates[0], ratePerUsd: 84.5, updatedAt: '2025-11-09T10:00:00' }, rates[1], rates[2]]);
    fixture.detectChanges();

    expect(text()).toContain('INR rate saved');
    expect(page().control({ ...rates[0], ratePerUsd: 84.5 }).value).toBe('84.5');
  });

  it('does not call the server for an invalid rate and says why', () => {
    page().control(rates[0]).setValue('-3');

    (page()['save'] as (r: FxRate) => void)(rates[0]);
    fixture.detectChanges();

    http.expectNone('/api/fx-rates/INR');
    expect(root().querySelector('.field-error')!.textContent).toContain('positive number');
  });

  it('shows the server message when a save is rejected', () => {
    page().control(rates[1]).setValue('151');

    (page()['save'] as (r: FxRate) => void)(rates[1]);
    http.expectOne('/api/fx-rates/JPY').flush({ detail: 'Rate must have at most 6 decimal places' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(root().querySelector('.field-error')!.textContent).toContain('at most 6 decimal places');
  });

  it('adds a new currency in upper case and clears the form', () => {
    page().newCode.setValue('chf');
    page().newRate.setValue('0.88');

    (page()['add'] as () => void)();

    const put = http.expectOne('/api/fx-rates/CHF');
    expect(put.request.body).toEqual({ ratePerUsd: 0.88 });
    put.flush({ currency: 'CHF', ratePerUsd: 0.88, fractionDigits: 2, updatedAt: '2025-11-09T10:00:00' });
    http.expectOne('/api/fx-rates').flush(rates);

    expect(page().newCode.value).toBe('');
    expect(page().newRate.value).toBe('');
  });

  it('rejects a malformed new currency code without a request', () => {
    page().newCode.setValue('EURO');
    page().newRate.setValue('0.9');

    (page()['add'] as () => void)();
    fixture.detectChanges();

    http.expectNone((r) => r.url.startsWith('/api/fx-rates/'));
    expect(text()).toContain('three-letter currency code');
  });

  it('shows the server message when adding fails (e.g. an unknown currency)', () => {
    page().newCode.setValue('ZZZ');
    page().newRate.setValue('2');

    (page()['add'] as () => void)();
    http.expectOne('/api/fx-rates/ZZZ').flush({ detail: "'ZZZ' is not a valid ISO 4217 currency code" }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(text()).toContain('not a valid ISO 4217 currency code');
  });
});
