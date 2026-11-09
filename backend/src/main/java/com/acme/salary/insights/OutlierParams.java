package com.acme.salary.insights;

/**
 * Tuning for outlier detection.
 *
 * @param minGroupSize     peer groups smaller than this are not evaluated (too few peers to say what is normal)
 * @param zThreshold       minimum |modified z-score| (median/MAD based); 3.5 is the standard Iglewicz-Hoaglin cut-off
 * @param minDeviationPct  minimum distance from the peer median, in percent, so statistically tight groups do not
 *                         flag differences that are too small to matter to HR
 */
public record OutlierParams(int minGroupSize, double zThreshold, double minDeviationPct) {

    public static final int DEFAULT_MIN_GROUP_SIZE = 5;
    public static final double DEFAULT_Z_THRESHOLD = 3.5;
    public static final double DEFAULT_MIN_DEVIATION_PCT = 20;

    public OutlierParams {
        if (minGroupSize < 3 || minGroupSize > 100) {
            throw new IllegalArgumentException("minGroupSize must be between 3 and 100");
        }
        if (zThreshold < 1 || zThreshold > 10) {
            throw new IllegalArgumentException("zThreshold must be between 1 and 10");
        }
        if (minDeviationPct < 0 || minDeviationPct > 200) {
            throw new IllegalArgumentException("minDeviationPct must be between 0 and 200");
        }
    }

    public static OutlierParams defaults() {
        return new OutlierParams(DEFAULT_MIN_GROUP_SIZE, DEFAULT_Z_THRESHOLD, DEFAULT_MIN_DEVIATION_PCT);
    }
}
