/**
 * Money helpers. The API stores every amount as an integer in the currency's minor units (paise, cents, whole yen),
 * so these functions convert at the edges and never use floating-point arithmetic on stored values.
 */

const digitsCache = new Map<string, number>();

/** Minor-unit digits for an ISO 4217 code: USD 2, JPY 0. */
export function fractionDigits(currency: string): number {
  let digits = digitsCache.get(currency);
  if (digits === undefined) {
    digits = new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions()
      .maximumFractionDigits!;
    digitsCache.set(currency, digits);
  }
  return digits;
}

/** e.g. formatMinor(830000000, 'INR') -> "₹8,300,000.00"; formatMinor(5000000, 'JPY') -> "¥5,000,000". */
export function formatMinor(minor: number, currency: string): string {
  const digits = fractionDigits(currency);
  return new Intl.NumberFormat('en', { style: 'currency', currency, currencyDisplay: 'narrowSymbol' }).format(
    minor / 10 ** digits,
  );
}

/** USD cents to a whole-dollar string for tables, e.g. 5489130 -> "$54,891". */
export function formatUsdCents(cents: number): string {
  return new Intl.NumberFormat('en', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0,
  }).format(cents / 100);
}

/** Compact form for chart axes and tiles, e.g. 600966977 -> "$6.0M". */
export function formatUsdCompact(cents: number): string {
  return new Intl.NumberFormat('en', {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    minimumFractionDigits: 0,
    maximumFractionDigits: 1,
  }).format(cents / 100);
}

/**
 * Parses what a person types ("1,234.50", "85000") into integer minor units, or null if it is not a valid amount.
 * Done on the decimal string so 0.1 + 0.2 style float errors can never reach the database. More decimals than the
 * currency has (e.g. "10.5" yen) is rejected rather than silently rounded.
 */
export function parseMajorToMinor(text: string, currency: string): number | null {
  const cleaned = text.replace(/[,\s]/g, '');
  if (!/^\d+(\.\d+)?$/.test(cleaned)) {
    return null;
  }
  const digits = fractionDigits(currency);
  const [whole, fraction = ''] = cleaned.split('.');
  if (fraction.length > digits) {
    return null;
  }
  const minor = Number(whole + fraction.padEnd(digits, '0'));
  return Number.isSafeInteger(minor) ? minor : null;
}

/** The inverse, for pre-filling an input: 830000000 INR -> "8300000.00". */
export function minorToInput(minor: number, currency: string): string {
  const digits = fractionDigits(currency);
  return (minor / 10 ** digits).toFixed(digits);
}
