package com.acme.salary.fx;

/**
 * The reference definition of how a local salary converts to USD. The reporting SQL implements the same formula,
 * and an integration test checks the two agree.
 */
public final class FxConverter {

    public static final long RATE_SCALE = 1_000_000L;

    private FxConverter() {
    }

    /**
     * @param salaryMinor     salary in the currency's minor units (e.g. paise, yen)
     * @param rateMicro       currency units per 1 USD, times 1,000,000
     * @param fractionDigits  the currency's minor-unit digits (USD 2, JPY 0)
     * @return the salary in USD cents, rounded half up
     */
    public static long toUsdCents(long salaryMinor, long rateMicro, int fractionDigits) {
        if (rateMicro <= 0) {
            throw new IllegalArgumentException("rate must be positive");
        }
        double majorUnits = salaryMinor / Math.pow(10, fractionDigits);
        double usd = majorUnits * RATE_SCALE / rateMicro;
        return Math.round(usd * 100);
    }
}
