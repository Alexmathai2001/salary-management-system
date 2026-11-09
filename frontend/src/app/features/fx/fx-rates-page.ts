import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { BehaviorSubject, scan, switchMap } from 'rxjs';
import { describeError } from '../../core/api-error';
import { FxApi } from '../../core/insights.api';
import { FxRate } from '../../core/models';
import { initialView, loadAsEvents, reduceView } from '../../core/view-state';

/** Returns an error message, or '' if `text` is a usable rate: positive, at most 6 decimal places (what the API stores). */
export function validateRate(text: string): string {
  const cleaned = text.trim();
  if (!/^\d+(\.\d+)?$/.test(cleaned)) {
    return 'Enter a positive number, e.g. 83.25';
  }
  if (Number(cleaned) <= 0) {
    return 'The rate must be greater than zero';
  }
  if ((cleaned.split('.')[1] ?? '').length > 6) {
    return 'At most 6 decimal places';
  }
  return '';
}

function currencyName(code: string): string {
  try {
    return new Intl.DisplayNames(['en'], { type: 'currency' }).of(code) ?? code;
  } catch {
    return code;
  }
}

@Component({
  selector: 'app-fx-rates-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatProgressBarModule],
  templateUrl: './fx-rates-page.html',
  styleUrl: './fx-rates-page.scss',
})
export class FxRatesPage {
  private readonly api = inject(FxApi);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly state = toSignal(
    this.reload$.pipe(
      switchMap(() => loadAsEvents(this.api.list())),
      scan(reduceView<FxRate[]>, initialView<FxRate[]>()),
    ),
    { initialValue: initialView<FxRate[]>() },
  );
  protected readonly rates = computed(() => this.state().data ?? []);

  /**
   * One draft input per currency. A plain Map, not a signal: controls are created lazily while the template renders, and
   * Angular forbids writing to a signal during rendering.
   */
  private readonly drafts = new Map<string, FormControl<string>>();
  protected readonly rowError = signal<Record<string, string>>({});
  protected readonly saving = signal<string | null>(null);
  protected readonly message = signal('');

  protected readonly newCode = new FormControl('', { nonNullable: true });
  protected readonly newRate = new FormControl('', { nonNullable: true });
  protected readonly addError = signal('');

  protected readonly currencyName = currencyName;

  protected control(rate: FxRate): FormControl<string> {
    let control = this.drafts.get(rate.currency);
    if (!control) {
      control = new FormControl(String(rate.ratePerUsd), { nonNullable: true });
      this.drafts.set(rate.currency, control);
    }
    return control;
  }

  protected changed(rate: FxRate): boolean {
    return this.control(rate).value.trim() !== String(rate.ratePerUsd);
  }

  protected save(rate: FxRate): void {
    const text = this.control(rate).value;
    const problem = validateRate(text);
    this.setRowError(rate.currency, problem);
    if (problem) {
      return;
    }
    this.persist(rate.currency, Number(text), (message) => this.setRowError(rate.currency, message));
  }

  protected add(): void {
    const code = this.newCode.value.trim().toUpperCase();
    const rate = this.newRate.value;
    if (!/^[A-Z]{3}$/.test(code)) {
      this.addError.set('Enter a three-letter currency code, e.g. CHF');
      return;
    }
    const problem = validateRate(rate);
    if (problem) {
      this.addError.set(problem);
      return;
    }
    this.addError.set('');
    this.persist(code, Number(rate), (message) => this.addError.set(message), () => {
      this.newCode.reset();
      this.newRate.reset();
    });
  }

  private persist(currency: string, ratePerUsd: number, onError: (message: string) => void, onDone?: () => void): void {
    this.saving.set(currency);
    this.message.set('');
    this.api.update(currency, ratePerUsd).subscribe({
      next: () => {
        this.saving.set(null);
        this.drafts.delete(currency); // re-read the saved value after the reload
        this.message.set(`${currency} rate saved. Insights now use ${ratePerUsd} per USD.`);
        onDone?.();
        this.reload$.next();
      },
      error: (err) => {
        this.saving.set(null);
        onError(describeError(err).message);
      },
    });
  }

  private setRowError(currency: string, message: string): void {
    this.rowError.update((e) => ({ ...e, [currency]: message }));
  }

  protected updated(rate: FxRate): string {
    return rate.updatedAt && rate.updatedAt > '2000' ? rate.updatedAt.slice(0, 10) : 'Initial value';
  }
}
