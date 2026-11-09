package com.acme.salary.insights;

import java.util.Arrays;

/** The columns insights can be grouped by. The SQL column is fixed here, never taken from user input. */
public enum Dimension {
    COUNTRY("country"),
    DEPARTMENT("department"),
    JOB_LEVEL("job_level"),
    JOB_TITLE("job_title");

    private final String column;

    Dimension(String column) {
        this.column = column;
    }

    String column() {
        return column;
    }

    public static Dimension parse(String value) {
        // Accept jobLevel, job-level and JOB_LEVEL alike.
        String normalised = value == null ? "" : value.trim()
                .replaceAll("([a-z])([A-Z])", "$1_$2").replace("-", "_").toUpperCase();
        return Arrays.stream(values()).filter(d -> d.name().equals(normalised)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown dimension '" + value + "'. Allowed: country, department, jobLevel, jobTitle"));
    }
}
