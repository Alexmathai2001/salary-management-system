package com.acme.salary.employee;

/** Optional directory filters. Null or blank values mean "no filter on this field". */
public record EmployeeFilter(
        String q,
        String country,
        String department,
        String jobTitle,
        String jobLevel,
        EmployeeStatus status) {
}
