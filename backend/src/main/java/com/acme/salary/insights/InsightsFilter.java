package com.acme.salary.insights;

/** Optional drill-down filters shared by all insight queries. Inactive employees are excluded by default. */
public record InsightsFilter(String country, String department, String jobLevel, String jobTitle,
        boolean includeInactive) {

    public static InsightsFilter none() {
        return new InsightsFilter(null, null, null, null, false);
    }
}
