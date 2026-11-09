export interface NiceScale {
  /** Upper bound of the axis (>= the data maximum). */
  max: number;
  step: number;
  /** 0, step, 2*step ... max */
  ticks: number[];
}

/**
 * A "nice" axis: round tick values (1, 2, 5 x 10^n) that cover the data, so gridlines read as 0 / 20K / 40K rather
 * than 0 / 17.4K / 34.8K. Bars always start at zero, so the axis does too.
 */
export function niceScale(maxValue: number, targetTicks = 5): NiceScale {
  if (!Number.isFinite(maxValue) || maxValue <= 0) {
    return { max: 1, step: 1, ticks: [0, 1] };
  }
  const rough = maxValue / targetTicks;
  const magnitude = 10 ** Math.floor(Math.log10(rough));
  const fraction = rough / magnitude;
  const nice = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10;
  const step = nice * magnitude;
  const max = Math.round(Math.ceil(maxValue / step - 1e-9) * step * 1e6) / 1e6;
  const ticks: number[] = [];
  for (let i = 0; i * step <= max + step / 1e6; i++) {
    ticks.push(Math.round(i * step * 1e6) / 1e6);
  }
  return { max, step, ticks };
}
