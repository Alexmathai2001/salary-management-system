package com.acme.salary.insights;

/**
 * Pay statistics for one group, in USD cents. {@code median} averages the two middle values for an even
 * headcount; {@code p25}/{@code p75} use the nearest-rank method.
 */
public record PayStats(String key, long headcount, long totalCents, long avgCents, long medianCents,
        long p25Cents, long p75Cents, long minCents, long maxCents) {
}
