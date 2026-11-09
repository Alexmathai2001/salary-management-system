import { niceScale } from './chart-scale';

describe('niceScale', () => {
  it('rounds the axis up to clean tick values', () => {
    expect(niceScale(87)).toEqual({ max: 100, step: 20, ticks: [0, 20, 40, 60, 80, 100] });
    expect(niceScale(86_400)).toEqual({ max: 100_000, step: 20_000, ticks: [0, 20_000, 40_000, 60_000, 80_000, 100_000] });
  });

  it('always covers the data maximum', () => {
    for (const max of [1, 3, 7, 49, 50, 51, 999, 1_234, 54_891, 600_966, 7_654_321]) {
      expect(niceScale(max).max).toBeGreaterThanOrEqual(max);
    }
  });

  it('does not add a needless extra tick when the maximum is already a tick value', () => {
    expect(niceScale(100).max).toBe(100);
    expect(niceScale(50_000, 5).max).toBe(50_000);
  });

  it('handles small fractional ranges without floating-point noise', () => {
    expect(niceScale(0.3).ticks).toEqual([0, 0.1, 0.2, 0.3]);
  });

  it('survives empty or invalid data', () => {
    for (const bad of [0, -5, NaN, Infinity]) {
      expect(niceScale(bad)).toEqual({ max: 1, step: 1, ticks: [0, 1] });
    }
  });
});
