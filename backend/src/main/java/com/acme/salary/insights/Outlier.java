package com.acme.salary.insights;

/**
 * An employee whose pay is far from their peers'. Peers share job title, job level and country.
 *
 * @param deviationPct signed distance from the peer median in percent (positive = paid more)
 * @param modifiedZ    robust z-score, or null when the peers' pay has no spread at all (MAD = 0)
 */
public record Outlier(long id, String employeeCode, String name, String country, String department, String jobTitle,
        String jobLevel, String currency, long salaryMinor, long usdCents, long peerMedianCents, int peerCount,
        double deviationPct, Double modifiedZ, Direction direction) {

    public enum Direction {
        HIGH, LOW
    }
}
