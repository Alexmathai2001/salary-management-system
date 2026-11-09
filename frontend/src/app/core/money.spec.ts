import {
  formatMinor,
  formatUsdCents,
  formatUsdCompact,
  fractionDigits,
  minorToInput,
  parseMajorToMinor,
  percentChange,
  todayIso,
} from './money';

describe('money', () => {
  describe('fractionDigits', () => {
    it('knows each currency\'s minor unit', () => {
      expect(fractionDigits('USD')).toBe(2);
      expect(fractionDigits('INR')).toBe(2);
      expect(fractionDigits('JPY')).toBe(0);
    });
  });

  describe('formatMinor', () => {
    it('formats cents and paise as major units', () => {
      expect(formatMinor(8_500_000, 'USD')).toBe('$85,000.00');
      expect(formatMinor(830_000_000, 'INR')).toContain('8,300,000.00');
    });

    it('does not divide yen by 100', () => {
      expect(formatMinor(5_000_000, 'JPY')).toContain('5,000,000');
      expect(formatMinor(5_000_000, 'JPY')).not.toContain('.');
    });
  });

  describe('USD helpers', () => {
    it('formats whole dollars', () => {
      expect(formatUsdCents(5_489_130)).toBe('$54,891');
    });

    it('formats compactly for tiles and axes', () => {
      expect(formatUsdCompact(600_966_977)).toBe('$6M');
      expect(formatUsdCompact(5_489_130)).toBe('$54.9K');
    });
  });

  describe('parseMajorToMinor', () => {
    it('parses whole and decimal amounts exactly', () => {
      expect(parseMajorToMinor('85000', 'USD')).toBe(8_500_000);
      expect(parseMajorToMinor('1234.5', 'USD')).toBe(123_450);
      expect(parseMajorToMinor('1234.56', 'USD')).toBe(123_456);
    });

    it('ignores thousands separators and spaces', () => {
      expect(parseMajorToMinor('8,300,000', 'INR')).toBe(830_000_000);
      expect(parseMajorToMinor(' 1 000 ', 'USD')).toBe(100_000);
    });

    it('is exact where floating point is not', () => {
      // 0.07 * 100 === 7.000000000000001 in floating point
      expect(parseMajorToMinor('0.07', 'USD')).toBe(7);
      expect(parseMajorToMinor('19.99', 'USD')).toBe(1999);
    });

    it('handles currencies without minor units', () => {
      expect(parseMajorToMinor('5000000', 'JPY')).toBe(5_000_000);
      expect(parseMajorToMinor('5000000.5', 'JPY')).toBeNull();
    });

    it('rejects more decimals than the currency has instead of rounding', () => {
      expect(parseMajorToMinor('10.999', 'USD')).toBeNull();
    });

    it('rejects anything that is not a plain positive amount', () => {
      for (const bad of ['', 'abc', '-5', '1e6', '12.', '.5', '1.2.3', '12abc']) {
        expect(parseMajorToMinor(bad, 'USD')).toBeNull();
      }
    });

    it('rejects amounts too large to store exactly', () => {
      expect(parseMajorToMinor('99999999999999999999', 'USD')).toBeNull();
    });
  });

  describe('minorToInput', () => {
    it('round-trips with parseMajorToMinor', () => {
      for (const [minor, currency] of [
        [8_500_000, 'USD'],
        [830_000_000, 'INR'],
        [5_000_000, 'JPY'],
        [7, 'USD'],
      ] as const) {
        expect(parseMajorToMinor(minorToInput(minor, currency), currency)).toBe(minor);
      }
    });

    it('pads to the currency\'s decimals', () => {
      expect(minorToInput(8_500_000, 'USD')).toBe('85000.00');
      expect(minorToInput(5_000_000, 'JPY')).toBe('5000000');
    });
  });

  describe('percentChange', () => {
    it('reports raises and cuts to one decimal', () => {
      expect(percentChange(2_000_000, 2_400_000)).toBe(20);
      expect(percentChange(2_000_000, 2_150_000)).toBe(7.5);
      expect(percentChange(2_000_000, 1_900_000)).toBe(-5);
    });

    it('is null when there is no previous salary', () => {
      expect(percentChange(null, 2_000_000)).toBeNull();
      expect(percentChange(0, 2_000_000)).toBeNull();
    });
  });

  describe('todayIso', () => {
    it('is an ISO date', () => {
      expect(todayIso()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    });
  });
});
