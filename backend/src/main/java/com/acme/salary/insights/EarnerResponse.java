package com.acme.salary.insights;

public record EarnerResponse(long id, String employeeCode, String name, String country, String department,
        String jobTitle, String jobLevel, String currency, long salaryMinor, long usdCents) {
}
